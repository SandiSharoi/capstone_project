package com.DAT.capstone_project.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.DAT.capstone_project.dto.AssignApproverDTO;
import com.DAT.capstone_project.dto.FormApplyDTO;
import com.DAT.capstone_project.model.AssignApproverEntity;
import com.DAT.capstone_project.model.DepartmentEntity;
import com.DAT.capstone_project.model.FormApplyEntity;
import com.DAT.capstone_project.model.TeamEntity;
import com.DAT.capstone_project.model.UsersEntity;
import com.DAT.capstone_project.repository.AssignApproverRepository;
import com.DAT.capstone_project.repository.FormApplyRepository;
import com.DAT.capstone_project.repository.TeamRepository;


import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional
public class FormService {

    @Autowired
    private TeamRepository teamRepository;
    
    @Autowired
    private AssignApproverRepository assignApproverRepository;

    @Autowired
    private FormApplyRepository formApplyRepository;

    @Autowired
    private ModelMapper modelMapper;


    @SuppressWarnings("null")
    public List<AssignApproverDTO> getApproversForUser(UsersEntity user) {
        TeamEntity team = user.getTeam(); // Check team_id first
        if (team == null) {
            DepartmentEntity department = user.getDepartment(); // Fall back to department_id
            if (department != null) {
                team = findTeamByDepartment(department);
            }
        }

    
        String userPosition = user.getPosition().getName();
        List<AssignApproverDTO> approvers = new ArrayList<>();
    
        // Logic for PM position (show all PM, DH, DivH)
        if ("PM".equalsIgnoreCase(userPosition)) {
            approvers.add(createAssignApproverDTO(team.getDh(), "DH"));
            approvers.add(createAssignApproverDTO(team.getDivh(), "DivH"));
        }
        // Logic for DH position (show DH and DivH only)
        else if ("DH".equalsIgnoreCase(userPosition)) {
            approvers.add(createAssignApproverDTO(team.getDivh(), "DivH"));
        }
        // Logic for other positions (show PM, DH, DivH)
        else {
            approvers.add(createAssignApproverDTO(team.getPm(), "PM"));
            approvers.add(createAssignApproverDTO(team.getDh(), "DH"));
            approvers.add(createAssignApproverDTO(team.getDivh(), "DivH"));
        }
    
        // Remove null values
        approvers.removeIf(Objects::isNull);

        return approvers;
    }
    
    // Helper method to find a team using department
    private TeamEntity findTeamByDepartment(DepartmentEntity department) {
        List<TeamEntity> teams = teamRepository.findAllByDepartment(department);
    
        if (teams.isEmpty()) {
            throw new IllegalArgumentException("No team found for the given department");
        }
    
        // Logic to select one team (e.g., the team with the smallest ID)
        return teams.stream()
                .filter(team -> team.getDh() != null && team.getDivh() != null) // Ensure DH and DivH are set
                .min(Comparator.comparing(TeamEntity::getId)) // Example: Select the team with the smallest ID
                .orElseThrow(() -> new IllegalArgumentException("No valid team found for the given department"));
    }
    

    
    private AssignApproverDTO createAssignApproverDTO(UsersEntity approver, String position) {
        if (approver == null) {
            return null;
        }
        AssignApproverDTO dto = new AssignApproverDTO();
        dto.setApproverId(approver.getId());
        dto.setApproverPosition(position);
        dto.setChecked(true); // Always set checked to true


        // Set checked to true for certain approvers based on logic
        if ("PM".equalsIgnoreCase(position)) {
            dto.setChecked(true); // Example: Set "PM" to true, or implement more logic as needed
        }


        
        return dto;
    }
    
    public void saveFormApply(FormApplyDTO formApplyDTO, List<String> assignedTo, String highestApprover, UsersEntity loggedInUser) {
        FormApplyEntity formApplyEntity = new FormApplyEntity();
        formApplyEntity.setEmployee(loggedInUser);
        formApplyEntity.setAppliedDate(LocalDate.now());
        formApplyEntity.setTask(formApplyDTO.getTask());
        formApplyEntity.setPlannedDate(formApplyDTO.getPlannedDate());
        formApplyEntity.setPlannedStartHour(formApplyDTO.getPlannedStartHour());
        formApplyEntity.setPlannedEndHour(formApplyDTO.getPlannedEndHour());
        formApplyEntity.setActualDate(formApplyDTO.getActualDate());
        formApplyEntity.setActualStartHour(formApplyDTO.getActualStartHour());
        formApplyEntity.setActualEndHour(formApplyDTO.getActualEndHour());
        formApplyEntity.setOvertimeDate(formApplyDTO.getOvertimeDate());
        formApplyEntity.setWorkType(formApplyDTO.getWorkType());
        formApplyEntity.setDescription(formApplyDTO.getDescription());
    
        int noOfApprovers = (int) assignedTo.stream()
            .filter(approver -> List.of("PM", "DH", "DIVH").contains(approver.toUpperCase()))
            .count();
        formApplyEntity.setNo_of_approvers(noOfApprovers);
    
        // Set the highest approver
        formApplyEntity.setHighest_approver(highestApprover);
    
        formApplyRepository.save(formApplyEntity);
    
        TeamEntity team = getTeamForUser(loggedInUser);
        if (team == null) {
            throw new IllegalArgumentException("User has no valid team or department for approvers.");
        }
    
        for (String approverPosition : assignedTo) {
            UsersEntity approver = switch (approverPosition.toUpperCase()) {
                case "PM" -> team.getPm();
                case "DH" -> team.getDh();
                case "DIVH" -> team.getDivh();
                default -> null;
            };
    
            if (approver == null) {
                throw new IllegalArgumentException("Approver for position " + approverPosition + " is not defined.");
            }
    
            AssignApproverEntity assignApproverEntity = new AssignApproverEntity();
            assignApproverEntity.setFormApply(formApplyEntity);
            assignApproverEntity.setApprover(approver);
            assignApproverEntity.setApproverPosition(approverPosition);
            assignApproverEntity.setFormStatus("Pending");
    
            assignApproverRepository.save(assignApproverEntity);
        }
    }
    
    



  
    public TeamEntity getTeamForUser(UsersEntity user) {
        TeamEntity team = user.getTeam();
        if (team == null) {
            DepartmentEntity department = user.getDepartment();
            if (department != null) {
                team = findTeamByDepartment(department);
            }
        }
        return team;
    }
    

    // Check Form Status......................................................

    public List<FormApplyDTO> getFormsByEmployee(UsersEntity employee) {
        List<FormApplyEntity> formEntities = formApplyRepository.findByEmployeeOrderByFormApplyIdAsc(employee);
        return formEntities.stream()
                .map(entity -> modelMapper.map(entity, FormApplyDTO.class))
                .collect(Collectors.toList());
    }
    

    @Transactional
    public FormApplyDTO getFormDetails(Long formId) {
        FormApplyEntity formApply = formApplyRepository.findById(formId)
                .orElseThrow(() -> new IllegalArgumentException("Form not found with ID: " + formId));


        FormApplyDTO formApplyDTO = modelMapper.map(formApply, FormApplyDTO.class);
        System.out.println("FormApplyDTO: " + formApplyDTO);
        return formApplyDTO;
    }


    // Form Apply Detail Edit.............................................................

    // Fetch form by ID and convert entity to DTO
    public FormApplyDTO getFormApplyById(Long formApplyId) {
        FormApplyEntity formApplyEntity = formApplyRepository.findById(formApplyId)
                .orElseThrow(() -> new IllegalArgumentException("Form not found with ID: " + formApplyId));
        return modelMapper.map(formApplyEntity, FormApplyDTO.class);
    }
    
    @Transactional
    public void updateFormApply(FormApplyDTO formApplyDTO) {
        FormApplyEntity formApplyEntity = formApplyRepository.findById(formApplyDTO.getFormApplyId())
                .orElseThrow(() -> new IllegalArgumentException("Form not found with ID: " + formApplyDTO.getFormApplyId()));
    
        // Update only editable fields
        if (formApplyDTO.getTask() != null) formApplyEntity.setTask(formApplyDTO.getTask());
        if (formApplyDTO.getPlannedDate() != null) formApplyEntity.setPlannedDate(formApplyDTO.getPlannedDate());
        if (formApplyDTO.getPlannedStartHour() != null) formApplyEntity.setPlannedStartHour(formApplyDTO.getPlannedStartHour());
        if (formApplyDTO.getPlannedEndHour() != null) formApplyEntity.setPlannedEndHour(formApplyDTO.getPlannedEndHour());
        if (formApplyDTO.getActualDate() != null) formApplyEntity.setActualDate(formApplyDTO.getActualDate());
        if (formApplyDTO.getActualStartHour() != null) formApplyEntity.setActualStartHour(formApplyDTO.getActualStartHour());
        if (formApplyDTO.getActualEndHour() != null) formApplyEntity.setActualEndHour(formApplyDTO.getActualEndHour());
        if (formApplyDTO.getOvertimeDate() != null) formApplyEntity.setOvertimeDate(formApplyDTO.getOvertimeDate());
        if (formApplyDTO.getWorkType() != null) formApplyEntity.setWorkType(formApplyDTO.getWorkType());
        if (formApplyDTO.getDescription() != null) formApplyEntity.setDescription(formApplyDTO.getDescription());
    
        formApplyEntity.setAppliedDate(LocalDate.now()); // Update applied date automatically
        formApplyRepository.save(formApplyEntity);
    }
    

}