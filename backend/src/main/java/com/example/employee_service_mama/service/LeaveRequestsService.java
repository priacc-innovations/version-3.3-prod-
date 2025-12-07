package com.example.employee_service_mama.service;

import com.example.employee_service_mama.model.LeaveRequest;
import com.example.employee_service_mama.model.Users;
import com.example.employee_service_mama.repository.LeaveRequestsRepository;
import com.example.employee_service_mama.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LeaveRequestsService {

    private final LeaveRequestsRepository leaveRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;

    public LeaveRequestsService(LeaveRequestsRepository leaveRepo, UserRepository userRepo,EmailService emailService) {
        this.leaveRepo = leaveRepo;
        this.userRepo = userRepo;
        this.emailService= emailService;
    }

    public LeaveRequest applyLeave(Integer userId, String start, String end, String reason) {

        Users user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LeaveRequest leave = LeaveRequest.builder()
                .user(user)
                .empid(user.getEmpid()) // add by venkatasagar 26/11/2025
                .startDate(java.time.LocalDate.parse(start))
                .endDate(java.time.LocalDate.parse(end))
                .reason(reason)
                .status("pending")
                .build();

        return leaveRepo.save(leave);
    }

    public List<LeaveRequest> getUserLeaves(Integer userId) {
        return leaveRepo.findLeaveByUserId(userId);
    }

    public List<LeaveRequest> getAllLeaves() {
        return leaveRepo.findAllByOrderByCreatedAtDesc();
    }

    public LeaveRequest approveLeave(Integer leaveId, Integer hrId) {
        LeaveRequest leave = leaveRepo.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave not found"));

        Users user = leave.getUser();
        Users hr = userRepo.findById(hrId)
                .orElseThrow(() -> new RuntimeException("HR not found"));

        leave.setStatus("approved");
        leave.setApprovalDate(OffsetDateTime.now());
        leave.setApprovedBy(hr);
        leave.setEmpid(user.getEmpid());

        leaveRepo.save(leave);

        // 📧 Send approval email
        String subject = "Leave Request Approved";
        String body = "Hello " + user.getFullName() + ",\n\n"
                + "Your leave request from " + leave.getStartDate()
                + " to " + leave.getEndDate()
                + " has been approved by " + hr.getFullName() + ".\n\n"
                + "Best Regards,\nTeamHub HR";

        emailService.sendEmail(user.getEmail(), subject, body);

        return leave;
    }


    public LeaveRequest rejectLeave(Integer leaveId, Integer hrId) {
        LeaveRequest leave = leaveRepo.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave not found"));

        Users user = leave.getUser();
        Users hr = userRepo.findById(hrId)
                .orElseThrow(() -> new RuntimeException("HR not found"));

        leave.setStatus("rejected");
        leave.setApprovalDate(OffsetDateTime.now());
        leave.setApprovedBy(hr);
        leave.setEmpid(user.getEmpid());

        leaveRepo.save(leave);

        // 📧 Send rejection email
        String subject = "Leave Request Rejected";
        String body = "Hello " + user.getFullName() + ",\n\n"
                + "Your leave request from " + leave.getStartDate()
                + " to " + leave.getEndDate()
                + " has been rejected by " + hr.getFullName() + ".\n\n"
                + "For more clarification, please contact HR.\n\n"
                + "Best Regards,\nTeamHub HR";

        emailService.sendEmail(user.getEmail(), subject, body);

        return leave;
    }

    public Map<String, Integer> leaveDatas(Integer userId) {
      return new HashMap<>();
    }
}
