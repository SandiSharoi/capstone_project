package com.DAT.capstone_project.service;

import com.DAT.capstone_project.dto.DepartmentDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.modelmapper.ModelMapper;

import com.DAT.capstone_project.dto.ChangePasswordDTO;
import com.DAT.capstone_project.dto.ChangePasswordResponse;
import com.DAT.capstone_project.dto.UsersDTO;
import com.DAT.capstone_project.model.UsersEntity;
import com.DAT.capstone_project.model.PositionEntity;
import com.DAT.capstone_project.model.TeamEntity;
import com.DAT.capstone_project.model.DepartmentEntity;
import com.DAT.capstone_project.model.RoleEntity;
import com.DAT.capstone_project.repository.UsersRepository;
import com.DAT.capstone_project.repository.PositionRepository;
import com.DAT.capstone_project.repository.TeamRepository;
import com.DAT.capstone_project.repository.DepartmentRepository;
import com.DAT.capstone_project.repository.RoleRepository;

import jakarta.servlet.http.HttpSession;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;


@Service
public class AdminService {

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // --- Authentication and Registration ---
    public Optional<UsersEntity> authenticate(String email, String password) {
        Optional<UsersEntity> user = usersRepository.findByEmail(email);

        // Use BCrypt to match the hashed password with the entered password
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        return user.filter(u -> passwordEncoder.matches(password, u.getPassword()));  // Use BCrypt to compare hashed passwords
    }

    public String authenticateAndRedirect(String email, String password, Model model, HttpSession session) {
        Optional<UsersEntity> user = authenticate(email, password);

        if (user.isPresent()) {
            UsersEntity loggedInUser = user.get();

            // Set both loggedInUser and userId in the session
            session.setAttribute("loggedInUser", loggedInUser);  // Store full user object
            session.setAttribute("userId", loggedInUser.getId());  // Store userId for checking in other requests

            model.addAttribute("user", modelMapper.map(loggedInUser, UsersDTO.class));

            // Get the role and position names
            String roleName = loggedInUser.getRole().getName();
            String positionName = loggedInUser.getPosition().getName();

            // Redirect based on role and position
            return redirectBasedOnRole(roleName, positionName , model);
        } else {
            model.addAttribute("error", "Invalid email or password");
            return "login";
        }
    }


    public String redirectBasedOnRole(String roleName, String position, Model model) {
        switch (roleName.toUpperCase()) {
            case "ADMIN":
                model.addAttribute("menu", List.of("Department List","Team List", "User Registration", "Registration List", "Form Apply", "Check Form Status"));
                return "dashboard";
            case "APPROVER":
                // Add the check for "DivH" position
                if ("Division Head".equalsIgnoreCase(position)) {
                    model.addAttribute("menu", List.of("Form Decision"));
                } else {
                    model.addAttribute("menu", List.of("Form Apply", "Check Form Status", "Form Decision"));
                }
                return "dashboard";
            case "EMPLOYEE":
                model.addAttribute("menu", List.of("Form Apply", "Check Form Status"));
                return "dashboard";
            default:
                model.addAttribute("error", "Unknown role: " + roleName);
                return "login";
        }
    }


    public String showRegistrationPage(Model model) {
        model.addAttribute("positions", positionRepository.findAll());
        model.addAttribute("teams", teamRepository.findAll());
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("user", new UsersDTO());
        return "registration";
    }

    // New method to get departments where pm_id is NULL
    public List<DepartmentDTO> getDepartmentsForPosition(String position) {
        List<Integer> departmentIds = new ArrayList<>();

        if ("Project Manager".equalsIgnoreCase(position)) {
            departmentIds = teamRepository.findDepartmentIdsWherePmIsNull();
        } else if ("Department Head".equalsIgnoreCase(position)) {
            departmentIds = teamRepository.findDepartmentIdsWhereDhIsNull();
        } else if ("Division Head".equalsIgnoreCase(position)) {
            departmentIds = teamRepository.findDepartmentIdsWhereDivhIsNull();
        }

        else{
            departmentIds = teamRepository.findDepartmentIdsWherePmIsNull();
        }
        if (!departmentIds.isEmpty()) {
            return departmentRepository.findByIdIn(departmentIds).stream()
                    .map(dept -> new DepartmentDTO(dept.getId(), dept.getName()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList(); // Ensures empty list if no valid departments
    }



    @Transactional
    public String registerUser(UsersDTO usersDTO, Model model) {

        try {
            // Check if email already exists
            if (usersRepository.existsByEmail(usersDTO.getEmail())) {
                model.addAttribute("error", "This email has already been used by another user.");
                model.addAttribute("positions", positionRepository.findAll());
                model.addAttribute("teams", teamRepository.findAll());
                model.addAttribute("departments", departmentRepository.findAll());
                model.addAttribute("roles", roleRepository.findAll());
                return "registration"; // Return to registration page with error
            }

            // Fetch position
            PositionEntity position = positionRepository.findById(usersDTO.getPosition().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid Position ID"));

            String user_position_name = position.getName();

            // Map DTO to Entity
            UsersEntity user = modelMapper.map(usersDTO, UsersEntity.class);
            user.setPosition(position);
            user.setRole(roleRepository.findById(usersDTO.getRole().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid Role ID")));


            // Check if the position is already assigned for the selected team or department...........................
            if (user_position_name.equalsIgnoreCase("Project Manager")) {
                TeamEntity team = teamRepository.findById(usersDTO.getTeam().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Invalid Team ID"));
                if (team.getPm() != null) {
                    model.addAttribute("error", "A Project Manager (PM) has already been assigned to this team.");
                    model.addAttribute("positions", positionRepository.findAll());
                    model.addAttribute("teams", teamRepository.findAll());
                    model.addAttribute("departments", departmentRepository.findAll());
                    model.addAttribute("roles", roleRepository.findAll());
                    return "registration"; // Return to registration page with error
                }
            }

            else if (user_position_name.equalsIgnoreCase("Department Head")) {
                DepartmentEntity department = departmentRepository.findById(usersDTO.getDepartment().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Invalid Department ID"));
                List<TeamEntity> teams = teamRepository.findByDepartmentId(department.getId());
                for (TeamEntity team : teams) {
                    if (team.getDh() != null) {
                        model.addAttribute("error", "A Department Head (DH) has already been assigned to this department.");
                        model.addAttribute("positions", positionRepository.findAll());
                        model.addAttribute("teams", teamRepository.findAll());
                        model.addAttribute("departments", departmentRepository.findAll());
                        model.addAttribute("roles", roleRepository.findAll());
                        return "registration"; // Return to registration page with error
                    }
                }
            }

            else if (user_position_name.equalsIgnoreCase("Division Head")) {
                List<Integer> departmentIds = usersDTO.getDepartmentIds();
                if (departmentIds == null || departmentIds.isEmpty()) {
                    throw new IllegalArgumentException("At least one department must be selected for DivH.");
                }
                List<TeamEntity> teams = teamRepository.findByDepartmentIdIn(departmentIds);
                for (TeamEntity team : teams) {
                    if (team.getDivh() != null) {
                        model.addAttribute("error", "A Division Head (DivH) has already been assigned to one or more of the selected departments.");
                        model.addAttribute("positions", positionRepository.findAll());
                        model.addAttribute("teams", teamRepository.findAll());
                        model.addAttribute("departments", departmentRepository.findAll());
                        model.addAttribute("roles", roleRepository.findAll());
                        return "registration"; // Return to registration page with error
                    }
                }
            }

            // Case 1: DH → Only department should be saved, team should be NULL
            if ( user_position_name.equalsIgnoreCase("Department Head")) {
                DepartmentEntity department = departmentRepository.findById(usersDTO.getDepartment().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Invalid Department ID"));



                user.setDepartment(department);
                user.setTeam(null); // Ensure team_id is NULL

                // Find all teams associated with the selected department and save dh_id
                List<TeamEntity> teamsToUpdate = teamRepository.findByDepartmentId(department.getId());
                for (TeamEntity team : teamsToUpdate) {
                    team.setDh(user); // Assign user as Dh in each team
                }
                teamRepository.saveAll(teamsToUpdate); // Save updated teams
            }

            // Case 2: DivH → Team & Department should be NULL, user is assigned to multiple departments
            else if (user_position_name.equalsIgnoreCase("Division Head")) {
                user.setTeam(null);  // Ensure team_id is NULL
                user.setDepartment(null);  // Ensure department_id is NULL

                // Fetch multiple departments selected for DivH
                List<Integer> departmentIds = usersDTO.getDepartmentIds();
                if (departmentIds == null || departmentIds.isEmpty()) {
                    throw new IllegalArgumentException("At least one department must be selected for DivH.");
                }

                // Save user first before updating teams (prevents TransientObjectException)
                user = usersRepository.save(user);

                // Find all teams associated with the selected departments and save divh_id
                List<TeamEntity> teamsToUpdate = teamRepository.findByDepartmentIdIn(departmentIds);
                for (TeamEntity team : teamsToUpdate) {
                    team.setDivh(user); // Assign user as DivH in each team
                }
                teamRepository.saveAll(teamsToUpdate); // Save updated teams
            }

            // Case 3: Other positions → Save both Team & Department
            else {
                TeamEntity team = teamRepository.findById(usersDTO.getTeam().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Invalid Team ID"));
                DepartmentEntity department = departmentRepository.findById(usersDTO.getDepartment().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Invalid Department ID"));
                user.setTeam(team);
                user.setDepartment(department);

                if (user_position_name.equalsIgnoreCase("PM")) {

                    // Fetch all teams associated with the specified team ID and department ID
                    List<TeamEntity> teamsToUpdate = teamRepository.findByIdAndDepartmentId(team.getId(), department.getId());
                    for (TeamEntity t : teamsToUpdate) {
                        t.setPm(user); // Assign user as PM in each team
                    }
                    teamRepository.saveAll(teamsToUpdate); // Save updated teams
                }
            }

            // Save user (if not DivH, as it's already saved earlier)
            if (!user_position_name.equalsIgnoreCase("Division Head")) {
                usersRepository.save(user);
            }

        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("positions", positionRepository.findAll());
            model.addAttribute("teams", teamRepository.findAll());
            model.addAttribute("departments", departmentRepository.findAll());
            model.addAttribute("roles", roleRepository.findAll());
            return "registration"; // Return to registration page with error
        }

        return "registration_success";
    }

    public String showRegistrationListPage(Model model) {
        List<UsersEntity> userEntities = usersRepository.findAllByOrderByIdAsc();
        List<UsersDTO> users = userEntities.stream()
                .map(entity -> modelMapper.map(entity, UsersDTO.class))
                .collect(Collectors.toList());
        model.addAttribute("users", users);
        return "registration_list"; // This should match your Thymeleaf template name
    }

    // --- UsersService methods merged here ---
    public List<UsersDTO> getAllUsers() {
        return usersRepository.findAll()
                .stream()
                .map(user -> modelMapper.map(user, UsersDTO.class))
                .toList();
    }

    public UsersDTO getUserById(Long id) {
        UsersEntity user = usersRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return modelMapper.map(user, UsersDTO.class);
    }

    public void updateUser(UsersDTO userDTO) {
        UsersEntity userEntity = usersRepository.findById(userDTO.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        userEntity.setName(userDTO.getName());
        userEntity.setEmail(userDTO.getEmail());
        userEntity.setPhone(userDTO.getPhone());

        if (userDTO.getDepartment() != null && userDTO.getDepartment().getId() != null) {
            DepartmentEntity department = departmentRepository.findById(userDTO.getDepartment().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Department not found"));
            userEntity.setDepartment(department);
        }

        if (userDTO.getPosition() != null && userDTO.getPosition().getId() != null) {
            PositionEntity position = positionRepository.findById(userDTO.getPosition().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Position not found"));
            userEntity.setPosition(position);
        }

        if (userDTO.getTeam() != null && userDTO.getTeam().getId() != null) {
            TeamEntity team = teamRepository.findById(userDTO.getTeam().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Team not found"));
            userEntity.setTeam(team);
        }

        if (userDTO.getRole() != null && userDTO.getRole().getId() != null) {
            RoleEntity role = roleRepository.findById(userDTO.getRole().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Role not found"));
            userEntity.setRole(role);
        }

        usersRepository.save(userEntity);
    }


    // User details View or Edit or Delete........................................................
    public Map<String, Object> getUserDetailsOrEdit(Long id, boolean edit) {
        UsersDTO user = getUserById(id);  // Get user details
        List<PositionEntity> positions = positionRepository.findAll();
        List<TeamEntity> teams = teamRepository.findAll();
        List<DepartmentEntity> departments = departmentRepository.findAll();
        List<RoleEntity> roles = roleRepository.findAll();

        Map<String, Object> data = new HashMap<>();
        data.put("user", user);
        data.put("positions", positions);
        data.put("teams", teams);
        data.put("departments", departments);
        data.put("roles", roles);
        data.put("isEditable", edit);

        return data;
    }

    public void deleteUser(Long id) {
        // Fetch all the teams where the user is referenced as PM, DH, or DivH
        List<TeamEntity> teams = teamRepository.findAll();

        for (TeamEntity team : teams) {
            if (team.getPm() != null && team.getPm().getId().equals(id)) {
                team.setPm(null);  // Remove the PM reference
            }
            if (team.getDh() != null && team.getDh().getId().equals(id)) {
                team.setDh(null);  // Remove the DH reference
            }
            if (team.getDivh() != null && team.getDivh().getId().equals(id)) {
                team.setDivh(null);  // Remove the DivH reference
            }
        }

        // After updating the references, save the changes
        teamRepository.saveAll(teams);

        // Now, delete the user from the users table
        usersRepository.deleteById(id);  // Deletes the user with the given ID
    }


    public UsersDTO getOriginalUserDetails(Long id) {
        UsersEntity userEntity = usersRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return modelMapper.map(userEntity, UsersDTO.class);
    }



    // Searh employee in registration list by name or id...................................................

    public List<UsersDTO> searchUsersByIdOrName(String idQuery, String nameQuery) {
        if (idQuery != null && !idQuery.isEmpty()) {
            try {
                Long id = Long.parseLong(idQuery);
                Optional<UsersEntity> userEntity = usersRepository.findById(id);
                return userEntity.map(entity -> Collections.singletonList(modelMapper.map(entity, UsersDTO.class)))
                        .orElse(Collections.emptyList());
            } catch (NumberFormatException e) {
                // Handle invalid ID format gracefully
                return Collections.emptyList();
            }
        }

        if (nameQuery != null && !nameQuery.isEmpty()) {
            List<UsersEntity> userEntities = usersRepository.findByNameContainingIgnoreCase(nameQuery);
            return userEntities.stream()
                    .map(entity -> modelMapper.map(entity, UsersDTO.class))
                    .collect(Collectors.toList());
        }

        // Return an empty list if both queries are empty
        return Collections.emptyList();
    }

    // Password Change..........................................................................................

    private boolean isValidPassword(String password) {
        // Ensure password is at least 8 characters long and contains at least one special character
        return password.length() >= 8 && password.matches(".*[!@#$%^&*(),.?\":{}|<>].*");
    }

    public ChangePasswordResponse changePassword(Long userId, ChangePasswordDTO changePasswordDTO, HttpSession session) {
        // Fetch the user from the database
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        System.out.println("Stored password: " + user.getPassword());
        System.out.println("Entered password: " + changePasswordDTO.getOldPassword());

        if (!passwordEncoder.matches(changePasswordDTO.getOldPassword(), user.getPassword())) {
            return new ChangePasswordResponse(false, "Old password is incorrect.");
        }

        if (!isValidPassword(changePasswordDTO.getNewPassword())) {
            return new ChangePasswordResponse(false, "Password must be at least 8 characters long and contain at least one special character.");
        }

        // Update password and save user
        user.setPassword(passwordEncoder.encode(changePasswordDTO.getNewPassword()));
        usersRepository.save(user);

        // Also update the session with the new password
        session.setAttribute("loggedInUser", user);

        return new ChangePasswordResponse(true, "Password changed successfully.");
    }
}