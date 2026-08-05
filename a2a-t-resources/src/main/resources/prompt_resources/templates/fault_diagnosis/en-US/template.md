## Task Type
Fault root cause diagnosis.

## Task Description
Based on the event information output by event recognition and the preliminary demarcation analysis, preliminary root cause analysis, and preliminary impact analysis, perform root cause localization for the fault event, and output the root cause localization information and the recommended handling plan.

## Task Target
{{task_target}} (required)
Requirement: State the specific requirements for the fault diagnosis task, such as completing root cause localization of the fault event, and outputting professional root cause localization information, professional classification of the fault root cause, fault root cause details, remote handling plan, on-site emergency repair plan, and service impact analysis.
Example: Please perform root cause localization for the fault event, and output professional root cause localization information, professional classification of the fault root cause, fault root cause details, remote handling plan, on-site emergency repair plan, and service impact analysis.

## Task Context
{{task_context}} (required)
Requirement: A fault root cause diagnosis task may contain multiple alarm events. Each event may contain the following information:
1. Fault event sequence number. Required. Example: 1856365516_2839324485_2130908106_4130674041
2. Event title name. Required. Example: 5G base station outage event
3. Alarm vendor internal sequence number associated with the event. Required. Example: 123211121244
4. Transport-domain alarm extended sequence number associated with the event. Optional.
5. Unique identifier of the associated alarm in the NMS. Required. Example: 0108-007-012-10-000003
6. Associated alarm title. Required. Example: gNodeB outage alarm
7. Event generation time. Required. Example: 2025-05-22 08:40:44
8. Alarm severity; the smaller the number, the more severe. Level 1 alarm (Critical); Level 2 alarm (Major); Level 3 alarm (Minor); Level 4 alarm (Warning). Required. Example: Level 4 alarm
9. Base station ID, used by the wireless workbench. Optional. Example: 2148660
10. Network element name. Required. Example: QZHA-HAZBYSDGG-HRHH
11. Circuit ID. Optional. Example: QZHAZBHNLX-QZHAJL10GE1097086NR
12. Equipment room information. Optional. Example: Self-built equipment room on the first floor of QZHAZBYS

Example: There is 1 alarm event to be diagnosed, with the following details:
- The event sequence number is 1856365516_2839324485_2130908106_4130674041, titled 5G base station outage event, the alarm vendor internal sequence number associated with the event is 123211121244, the unique identifier of the associated alarm in the NMS is 0108-007-012-10-000003, the associated alarm title is gNodeB outage alarm, the event generation time is 2025-05-22 08:40:44, the alarm severity is level 4, and the network element name is QZHA-HAZBYSDGG-HRHH.



## Expected Output
{{expected_output}} (optional)
Requirement: For the expected fault diagnosis result to be returned, the following information must be declared:
1. Declare which type of A2A Part carries it, such as DataPart or TextPart.
2. Declare which information elements are included (e.g., diagnosis result, localization conclusion, localization analysis process, etc.).
Example: Require the fault diagnosis result to be returned via TextPart. The specific content of the fault diagnosis result is required as follows:
1. The allowed values of the fault root cause localization result include: successful, rejected, failed, and other.
2. Fault root cause details, including the name, details, and repair suggestions (including remote handling and on-site emergency repair) of the fault root cause, the localization analysis process, the service impact, and the identifier, type, name, and detailed location of the resource object where the fault root cause point resides.
