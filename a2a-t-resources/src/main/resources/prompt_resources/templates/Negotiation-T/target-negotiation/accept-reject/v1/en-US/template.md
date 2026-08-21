## Negotiation Context
{{negotiation_context}} (required)
Requirement:
Each negotiation must contain the following information:
- id: The unique identifier of a negotiation session, using UUID. When the negotiation enters a terminal state (Accept or Reject), this negotiation session terminates. Example: 3dbc13b5-bd57-4c2b-b503-24e381b6c8d3
- round: The current round. Type: positive integer. Example: 1
- maxRounds: The maximum number of rounds. When this number is exceeded, the negotiation ends. Type: positive integer. Example: 5

## Target Negotiation Result
{{target_negotiation_result}} (required)
Requirement:
1. If all questions can be clarified, return Accept
2. If all questions cannot be fully clarified, return Reject
3. The conclusion must be one of the two, and vague states such as "partially agreed" are not allowed

## Target Negotiation Result Content
{{target_negotiation_result_content}} (required)
Requirement:
1. If all questions can be clarified, list the finally confirmed intent content:
	- Summarize the understandings that have been confirmed or adopted after correction in all rounds of this negotiation, to form a complete and unambiguous final intent description, rather than listing the change process of each round
	- Each item must be directly usable as the basis for subsequent task execution, and must no longer contain uncertain statements such as "to be confirmed" or "may be"
2. If all questions cannot be fully clarified, state the reason for failure
