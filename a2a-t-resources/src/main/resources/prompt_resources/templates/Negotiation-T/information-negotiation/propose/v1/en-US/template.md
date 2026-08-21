## Negotiation Context
{{negotiation_context}} (required)
Requirement:
Each negotiation must contain the following information:
- id: The unique identifier of a negotiation session, using UUID. When the negotiation enters a terminal state (Accept or Reject), this negotiation session terminates. Example: 3dbc13b5-bd57-4c2b-b503-24e381b6c8d3
- round: The current round. Type: positive integer. Example: 1
- maxRounds: The maximum number of rounds. When this number is exceeded, the negotiation ends. Type: positive integer. Example: 5

## Information Negotiation
Please supplement the relevant content based on <Required Information Items>.

## Required Information Items
{{required_information_items}} (required)
Requirement:
Provide the name (required), meaning (optional), format requirement (optional), example (optional), and the relationship between missing items (AND/OR) (optional) for each missing item.
Example:
1. Energy saving area information, e.g., Songshanhu
2. Energy saving rate guarantee goal, e.g., 20Mbps
3. VLANId, e.g., xxxx(cvlan=11)
