## Task Type
SPN private line complaint diagnosis

## Task Description
Requirement: Provide an overall overview of the task. Based on <Task Object>, <Task Context>, and <Constraints>, perform network-side fault root cause diagnosis in the complaint scenario, achieve the complaint diagnosis goal defined in <Task Target>, and return the task processing result in the structure defined in <Expected Output>.

## Task Target
Requirement:
1. Based on the fault phenomenon of the private line service, diagnose and return the network-side fault root cause

## Task Object
{{task_object}} (optional)
Requirement: Provide the private line service object. Provide one of the following three parameters to identify the private line service object: private line name, access port name, and access port resource ID.
- Private line name: the private line object identifier in the network-side EMS NMS.
- Access port name: the access port name in the network-side EMS NMS, in the format: network element name + board number + board model + port number.
- Access port resource ID: the access port resource object identifier in the network-side EMS NMS.
Example:
Access port resource ID: f47ac10b-58cc-4372-a567-0e02b2c3d479

## Task Context
{{task_context}} (required)
Requirement: Provide the fault phenomenon description of the private line service and the context information of the diagnosis task.
1. Fault phenomenon: includes two scenarios, "private line interruption" and "poor private line quality". Required parameter. Example: "private line interruption"
2. Fault occurrence time. Optional parameter. Example: "2024-01-16T08:21:46Z"
3. Fault or event sequence number on the OSS side. Optional parameter. Example: "fault-id-1-017-20230511-09013"

## Expected Output
{{expected_output}} (optional)
Requirement:
1. Include the diagnosis result type, diagnosis result details, and repair suggestions.
2. The allowed values of the diagnosis result type parameter include: diagnosis successful, diagnosis failed, diagnosis not started, or diagnosis not finished.
3. Multiple fault root causes may be included. Each fault root cause includes: the name of the fault root cause, detailed description, repair suggestions, and the identifier, type, name, and detailed location of the resource object where the fault root cause point resides. The detailed description of the overall fault, and the repair suggestions for the fault root cause.
4. If multiple fault root causes exist, the detailed descriptions and repair suggestions across the multiple fault root causes must be summarized and refined into the overall diagnosis result details and repair suggestions.
