# 1. Release alerts use a per-device follow list

Date: 2026-10-08
Status: Accepted

## Decision

Each device sends the server its Firebase Cloud Messaging token and the list of series it follows, through `PUT /devices/me/follows`. The server polls release feeds for the union of all lists and pushes each alert to each device directly.

## Alternative considered

Firebase topics, one per series. Devices subscribe with Firebase directly and the server never learns who follows what. Rejected because:

- Topic messages can arrive minutes late.
- Subscriptions fail silently and the server cannot see them, which makes "I didn't get an alert" hard to debug.
- Knowing which series to poll needs an extra anonymous re-announce scheme.

## Consequences

The server stores personal data: a push token linked to a reading list. Before release:

- [ ] The privacy policy states that the server stores the device's push token and the list of followed series, why, and for how long.
- [ ] Users can delete this data. `DELETE /devices/me` removes the device, its token and its follow list.
- [ ] Tokens that FCM reports as unregistered are deleted, along with their follow lists.
- [ ] A retention limit is set for devices that stop calling the API.
