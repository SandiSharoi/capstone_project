package com.DAT.capstone_project.controller;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.DAT.capstone_project.dto.DepartmentDTO;
import com.DAT.capstone_project.dto.TeamDTO;
import com.DAT.capstone_project.model.DepartmentEntity;
import com.DAT.capstone_project.model.TeamEntity;
import com.DAT.capstone_project.repository.DepartmentRepository;
import com.DAT.capstone_project.repository.TeamRepository;
import com.DAT.capstone_project.service.DepartmentAndTeamService;


@Controller
public class DepartmentAndTeamController {

    @Autowired
    private DepartmentAndTeamService departmentAndTeamService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private DepartmentRepository departmentRepository;




    // Department ..............................................................
   
    @GetMapping("/department_list")
    public String departmentlistPage(Model model) {
        return departmentAndTeamService.showdepartmentListPage(model);
    }

    @PostMapping("/department_registration")
    public String departmentRegistration(RedirectAttributes redirectAttributes,
                @ModelAttribute DepartmentEntity department) {
        
        if (departmentAndTeamService.isDepartmentNameDuplicate(department.getName())) {
                redirectAttributes.addFlashAttribute("error", "Department Name Already Exists");
                return "redirect:/department_list"; // Redirect back to the department list page
            }
        departmentRepository.save(department);
        redirectAttributes.addFlashAttribute("message", "Department added successfully!");
        return "redirect:/department_list"; // Redirect back to the department list page
    }


    @PostMapping("/department_update")
    public String updateDepartment(@ModelAttribute DepartmentDTO departmentDTO, RedirectAttributes redirectAttributes) {
        if (departmentAndTeamService.isDuplicateDepartmentNameOnUpdate(departmentDTO)) {
            redirectAttributes.addFlashAttribute("error", "Department Name Already Exists");
            return "redirect:/department_list"; // Redirect back to the department list page
        }
        departmentAndTeamService.updateDepartment(departmentDTO);
        redirectAttributes.addFlashAttribute("message", "Department updated successfully!");
        return "redirect:/department_list"; // Redirect back to the department list page
    }    

    // Team ................................................................................

    @GetMapping("/team_list")
    public String teamlistPage(Model model) {
        return departmentAndTeamService.showteamListPage(model);
    }
     
    @PostMapping("/team_registration")
    public String teamregistrationPost(Model model, @ModelAttribute TeamEntity teams,
                RedirectAttributes redirectAttributes) {
            
        // Save the populated `teams` object received from the form
        Optional<TeamEntity> teamOptional = departmentAndTeamService.isTeamNameUpdate(teams);

        if (teamOptional.isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Team Name Already Exists");
            return "redirect:/team_list"; // Redirect to team list page with error
        }
        teamRepository.save(teams);

        // Redirect with a success flash message
        redirectAttributes.addFlashAttribute("message", "Team registered successfully!");
        return "redirect:/team_list"; // Redirect to team list page
    }


    @PostMapping("/team_update")
    public String updateTeam(@ModelAttribute TeamDTO teamDTO, RedirectAttributes redirectAttributes) {
        System.out.println("Team Name: " + teamDTO.getName());

        if (teamDTO.getDepartment() == null || teamDTO.getDepartment().getId() == null) {
            redirectAttributes.addFlashAttribute("error", "Invalid Department Selection");
            return "redirect:/team_list";
        }

        System.out.println("Department ID: " + teamDTO.getDepartment().getId());

        Optional<TeamEntity> teamOptional = departmentAndTeamService.isDuplicateTeamNameOnUpdate(teamDTO);

        if (teamOptional.isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Team Name Already Exists");
            return "redirect:/team_list";
        }

        departmentAndTeamService.updateTeamService(teamDTO);
        redirectAttributes.addFlashAttribute("message", "Team updated successfully!");
        return "redirect:/team_list";
    }



    @PostMapping("/departments/delete/{id}")
    public String deleteDepartment(@PathVariable Integer id, Model model, RedirectAttributes redirectAttributes) {
        // Check if there are any teams linked to this department
        if (departmentAndTeamService.hasTeamsLinkedToDepartment(id)) {
            // If teams are found, add an error message

            redirectAttributes.addFlashAttribute("error",
                    "There are existing teams linked to this department. Unable to delete.");

            return "redirect:/department_list";
        }

        // If no teams are linked, proceed with deletion
        departmentAndTeamService.deleteDepartment(id);

        // Add a success message to be displayed on the next request
        redirectAttributes.addFlashAttribute("message", "Department deleted successfully.");

        return "redirect:/department_list"; // Redirect back to the department list page
    }

}