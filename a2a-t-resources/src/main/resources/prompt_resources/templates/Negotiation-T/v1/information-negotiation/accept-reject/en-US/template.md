## Negotiation Context
{{negotiation_context}} (required)
Requirement:
Each negotiation must contain the following information:
- id: The unique identifier of a negotiation session, using UUID. When the negotiation enters a terminal state (Accept or Reject), this negotiation session terminates. Example: 3dbc13b5-bd57-4c2b-b503-24e381b6c8d3
- round: The current round. Type: positive integer. Example: 1
- maxRounds: The maximum number of rounds. When this number is exceeded, the negotiation ends. Type: positive integer. Example: 5

## Information Negotiation Result
{{information_negotiation_result}} (required)
Requirement:
1. If the required information can be provided, return Accept
2. If the required information cannot be fully provided, return Reject
3. The conclusion must be one of the two

## Information Negotiation Result Content
{{information_negotiation_result_content}} (required)
Requirement:
1. If the information can be provided:
	- State the name and content
2. If the information cannot be provided:
	- State the name and the specific reason why it cannot be provided (optional)
Example 1:
Energy saving area information: Songshanhu
Energy saving rate guarantee goal: 20Mbps
Example 2:
Unable to provide area information
