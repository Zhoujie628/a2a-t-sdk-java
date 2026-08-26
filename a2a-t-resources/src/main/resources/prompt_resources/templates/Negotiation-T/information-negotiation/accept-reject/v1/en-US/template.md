## Information Negotiation Result
{{information_negotiation_result}} (required)
Requirement:
1. If the required information can be provided, return Accept
2. If the required information cannot be fully provided, return Reject
3. The conclusion must be one of the two

## Information Negotiation Result Content
{{information_negotiation_result_content}} (required)
Requirement:
1. When the conclusion is Accept: state each item as "name: content", where the content is the actual value of the information
2. When the conclusion is Reject: state each item as "name: reason why it cannot be provided", where the reason is the specific cause preventing the supplement
Example 1 (Accept):
Energy saving area information: Songshanhu
Energy saving rate guarantee goal: 20Mbps
Example 2 (Reject):
Energy saving area information: site inventory unavailable, cannot provide
Energy saving rate guarantee goal: goal not confirmed with the customer, cannot provide
