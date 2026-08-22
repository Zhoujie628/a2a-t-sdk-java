## Negotiation Context
{{negotiation_context}} (required)
Requirement:
Each negotiation must contain the following information:
- id: The unique identifier of a negotiation session, using UUID. When the negotiation enters a terminal state (Accept, Reject, Abort), this negotiation session terminates. Example: 3dbc13b5-bd57-4c2b-b503-24e381b6c8d3
- round: The current round. Type: positive integer. Example: 1
- maxRounds: The maximum number of rounds. When this number is exceeded, the negotiation ends. Type: positive integer. Example: 5

## Negotiation Result
Abort

## Negotiation Termination Reason
{{negotiation_termination_reason}} (required)
Requirement:
State the reason for negotiation termination, such as reaching the negotiation round limit, timeout, token consumption limit, etc.
Example:
Reached the negotiation round limit. This negotiation is confirmed and ended.
