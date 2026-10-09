//! adblock-rust behind a small UniFFI interface for the Android app.
//!
//! The app builds an engine from filter list text once, saves its snapshot, and loads
//! the snapshot on later starts. It asks the engine about each request and each page.

use std::collections::HashSet;
use std::sync::Arc;

use adblock::Engine;
use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;

uniffi::setup_scaffolding!();

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum AdblockError {
    /// The snapshot was written by another engine version or is damaged.
    #[error("snapshot can't be read: {reason}")]
    BadSnapshot { reason: String },
}

/// What the page needs hidden, from the engine's cosmetic filters for one URL.
#[derive(uniffi::Record)]
pub struct PageCosmetics {
    /// CSS selectors to hide on this page.
    pub hide_selectors: Vec<String>,
    /// Generic selectors this page excepts. Pass them back to `hidden_selectors`.
    pub exceptions: Vec<String>,
    /// True when generic hiding is off for this page, so class and id names need no lookup.
    pub generichide: bool,
}

#[derive(uniffi::Object)]
pub struct AdblockEngine {
    engine: Engine,
}

#[uniffi::export]
impl AdblockEngine {
    /// Parses filter lists, each given as the full text of one list.
    #[uniffi::constructor]
    pub fn from_lists(lists: Vec<String>) -> Arc<Self> {
        let mut set = FilterSet::new(false);
        for list in lists {
            set.add_filter_list(list, ParseOptions::default());
        }
        Arc::new(Self {
            engine: Engine::new_with_filter_set(set),
        })
    }

    /// Loads an engine saved by `snapshot`, much faster than parsing the lists again.
    #[uniffi::constructor]
    pub fn from_snapshot(snapshot: Vec<u8>) -> Result<Arc<Self>, AdblockError> {
        let mut engine = Engine::default();
        engine
            .deserialize(&snapshot)
            .map_err(|e| AdblockError::BadSnapshot {
                reason: format!("{e:?}"),
            })?;
        Ok(Arc::new(Self { engine }))
    }

    pub fn snapshot(&self) -> Vec<u8> {
        self.engine.serialize()
    }

    /// Whether to block a request for `url` made by the page at `source_url`.
    /// `request_type` is a filter list type such as "document", "script" or "image".
    pub fn should_block(&self, url: String, source_url: String, request_type: String) -> bool {
        match Request::new(&url, &source_url, &request_type, "get") {
            Ok(request) => self.engine.check_network_request(&request).should_block(),
            // A URL the engine can't parse is never one a list names.
            Err(_) => false,
        }
    }

    pub fn page_cosmetics(&self, url: String) -> PageCosmetics {
        let resources = self.engine.url_cosmetic_resources(&url);
        PageCosmetics {
            hide_selectors: resources.hide_selectors.into_iter().collect(),
            exceptions: resources.exceptions.into_iter().collect(),
            generichide: resources.generichide,
        }
    }

    /// Selectors from generic rules that name any of these classes or ids, less `exceptions`.
    pub fn hidden_selectors(
        &self,
        classes: Vec<String>,
        ids: Vec<String>,
        exceptions: Vec<String>,
    ) -> Vec<String> {
        let exceptions: HashSet<String> = exceptions.into_iter().collect();
        self.engine
            .hidden_class_id_selectors(classes, ids, &exceptions)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const LIST: &str = "\
||ads.example^
||tracker.example^$third-party
@@||ads.example/allowed^
reader.example##.sticky-banner
##.ad-slot
reader.example#@#.ad-slot
";

    fn engine() -> Arc<AdblockEngine> {
        AdblockEngine::from_lists(vec![LIST.to_string()])
    }

    fn blocks(e: &AdblockEngine, url: &str, kind: &str) -> bool {
        e.should_block(
            url.into(),
            "https://reader.example/chapter-1".into(),
            kind.into(),
        )
    }

    #[test]
    fn listed_requests_are_blocked_and_exceptions_are_not() {
        let e = engine();
        assert!(blocks(&e, "https://ads.example/banner.js", "script"));
        assert!(!blocks(&e, "https://ads.example/allowed/x.js", "script"));
        assert!(blocks(&e, "https://tracker.example/pixel.gif", "image"));
        assert!(!blocks(
            &e,
            "https://cdn.reader.example/page-1.webp",
            "image"
        ));
    }

    #[test]
    fn a_url_that_does_not_parse_is_allowed() {
        assert!(!blocks(&engine(), "not a url", "image"));
    }

    #[test]
    fn a_snapshot_loads_into_the_same_engine() {
        let copy = AdblockEngine::from_snapshot(engine().snapshot()).unwrap();
        assert!(blocks(&copy, "https://ads.example/banner.js", "script"));
    }

    #[test]
    fn a_damaged_snapshot_is_an_error() {
        assert!(AdblockEngine::from_snapshot(vec![1, 2, 3]).is_err());
    }

    #[test]
    fn page_cosmetics_name_the_site_selectors_and_exceptions() {
        let page = engine().page_cosmetics("https://reader.example/chapter-1".into());
        assert_eq!(page.hide_selectors, vec![".sticky-banner"]);
        assert_eq!(page.exceptions, vec![".ad-slot"]);
        assert!(!page.generichide);
    }

    #[test]
    fn generic_selectors_come_from_class_names_less_exceptions() {
        let e = engine();
        let hidden = e.hidden_selectors(vec!["ad-slot".into(), "page".into()], vec![], vec![]);
        assert_eq!(hidden, vec![".ad-slot"]);
        let excepted = e.hidden_selectors(vec!["ad-slot".into()], vec![], vec![".ad-slot".into()]);
        assert!(excepted.is_empty());
    }

    /// The Ascon list as shipped, so its rules are checked against the ads they were written for.
    #[test]
    fn ascon_list_stops_overlay_links() {
        let list = include_str!("../../src/main/assets/adblock/ascon.txt");
        let e = AdblockEngine::from_lists(vec![list.to_string()]);
        let page = "https://manga.example/home";
        let document = |url: &str| e.should_block(url.into(), page.into(), "document".into());
        assert!(document(
            "https://sowve.com/4/739684b2a2af3eaf9109e8ffbaec4993"
        ));
        assert!(document(
            "https://rotating.example/4/739684b2a2af3eaf9109e8ffbaec4993"
        ));
        assert!(!document(
            "https://manga.example/read/solo-leveling/chapter-4"
        ));
        let hidden = e.page_cosmetics(page.into()).hide_selectors;
        assert!(
            hidden.iter().any(|s| s.contains("position: fixed")),
            "{hidden:?}"
        );
    }
}
