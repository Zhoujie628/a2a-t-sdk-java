## Task Type
Wireless energy efficiency optimization

## Task Description
Based on <Task Object>, <Task Context>, and <Constraints>, output an energy efficiency optimization plan that achieves the energy efficiency optimization goal defined in <Task Target>, and return the energy efficiency optimization result in the structure defined in <Expected Output>.

## Task Target
{{task_target}} (optional)
Requirement: Define the quantitative or qualitative goals the task must achieve, such as energy efficiency goals and rate guarantee goals. All are optional parameters.
Specific goal description for task-level energy saving:
1. Energy consumption goal: the gain target of energy saving. The target value format must be a percentage, such as the percentage by which total power consumption is reduced. Example: Energy consumption goal: 30%
2. Rate guarantee goal: the minimum downlink throughput target that must be guaranteed. Example: Rate guarantee goal: 50Mbps/s

## Task Object
{{task_object}} (required)
Requirement: Explicitly specify the energy saving area information, a geographic or logical area (e.g., district-level administrative unit) or longitude/latitude coordinates. The area information is a required parameter.
The following are specific descriptions:
1. Geographic/logical area name. Example: Area: "Songshanhu Administration Committee", coordinate (112.0, 20.6) area
2. **Important**: The area information must be a concrete physical address, for example a real physical community such as "Songshanhu Administration Committee", rather than a description without a specific physical location such as "all communities", "indoor communities", or "outdoor communities".

## Task Context
{{task_context}} (optional)
Requirement: Provide the background and context information for task execution. Optional parameter.

## Constraints
{{constraints}} (optional)
Requirement: List the rules, restrictions, and boundary conditions that must be observed during task execution. Constraints apply to all scenarios within the area by default; constraints applicable only to specified scenarios within the area should be stated separately. All are optional parameters.
1. Cell type to apply energy saving (indoor/outdoor/all). Example: Cell type for energy saving: outdoor
2. Cell radio access technology for energy saving (LTE/NR/all). Example: Cell RAT for energy saving: NR
3. Frequency requirements for the base layer and capacity layer. Frequencies must be expressed as downlink frequency numbers. Example: 1650 frequency as the base layer for carrier shutdown
4. Whether the energy saving aggressiveness is aggressive or conservative. Example: Energy saving aggressiveness: aggressive
5. Whether experience-lossless energy saving is required. Example: Experience-lossless energy saving required: yes
6. The time range for executing energy saving, recurring daily. Example: Energy saving time range: start time "08:00:00", end time "12:00:00"
7. Constraints applicable to specified scenarios within the area (e.g., railway/black forest/business district/residential area). Example: In the business district scenario, the 1300 frequency is forbidden to serve as the capacity layer; in the black forest scenario, enabling sleep is forbidden between 0:00 and 6:00.
8. Whether turning off carrier frequencies is allowed during energy saving. Example: Carrier shutdown frequency special requirement: cannot be shut down

## Expected Output
Requirement: Output the intent report for the energy efficiency optimization task. The intent report should contain the following information:
### Intent Report Basic Information
1. Unique identifier of the energy efficiency optimization report
2. Unique identifier of the energy saving task
3. Execution result of the overall intent, including the execution result status and execution result details. If the goal is not achieved, describe the specific reason for not achieving it.
### Intent Report Detailed Information
1. Execution result of specific KPIs, including the execution result status and execution result details. If the goal is not achieved, describe the specific reason for not achieving it.
