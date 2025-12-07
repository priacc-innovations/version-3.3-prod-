package com.example.employee_service_mama.service;

import com.example.employee_service_mama.dto.ForgotPasswordRequest;
import com.example.employee_service_mama.model.Users;
import com.example.employee_service_mama.repository.*;
import com.example.employee_service_mama.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {
    private final PasswordEncoder passwordEncoder;

    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestsRepository leaveRequestsRepository;
    private final AttendanceRecordsRepository recordsRepo;
    private final EmailService emailService;
    private final S3Client s3;
    private final JwtUtil jwtUtil;

    private final String BUCKET = "teamhub-storage";
    private final String S3_BASE_URL = "https://teamhub-storage.s3.us-east-1.amazonaws.com/";

    // ---------------- LOGIN ----------------
    public Map<String, Object> signin(String email, String rawPassword) {

        Users user = userRepository.findByEmailOnly(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        String token = jwtUtil.generateToken(email);

        user.setPassword(null);
        user.setResetOtp(null);
        user.setResetOtpExpiry(null);

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("user", user);

        return response;
    }

    // ---------------- GET USER WITH PUBLIC IMAGE URL ----------------
    public Users getUserById(Integer id) {
        Users user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getPhotoUrl() != null && !user.getPhotoUrl().startsWith("http")) {
            user.setPhotoUrl(S3_BASE_URL + user.getPhotoUrl());
        }

        return user;
    }

    // ---------------- UPDATE PROFILE ----------------
    public Users updateProfile(Integer id, Users data) {
        Users user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setFullName(data.getFullName());
        user.setEmail(data.getEmail());
        user.setPhone(data.getPhone());
        user.setDob(data.getDob());
        user.setAddress1(data.getAddress1());
        user.setAddress2(data.getAddress2());
        user.setCity(data.getCity());
        user.setState(data.getState());
        user.setCountry(data.getCountry());
        user.setPincode(data.getPincode());

        return userRepository.save(user);
    }

    // ---------------- UPLOAD PHOTO ----------------
    public String uploadPhoto(Integer id, MultipartFile file) {
        try {
            String key = "profile-pics/" + id + ".jpg";

            PutObjectRequest put = PutObjectRequest.builder()
                    .bucket(BUCKET)
                    .key(key)
                    .contentType("image/jpeg")
                    .build();

            s3.putObject(put, software.amazon.awssdk.core.sync.RequestBody.fromBytes(file.getBytes()));

            Users user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // ✔ store only key in DB
            user.setPhotoUrl(key);
            userRepository.save(user);

            // ✔ return full permanent public URL
            return S3_BASE_URL + key;

        } catch (Exception e) {
            throw new RuntimeException("Photo upload failed: " + e.getMessage());
        }
    }

    // ---------------- LIST USERS ----------------
    public List<Users> getAllUsers() {
        List<Users> list = userRepository.findAll();

        for (Users u : list) {
            if (u.getPhotoUrl() != null && !u.getPhotoUrl().startsWith("http")) {
                u.setPhotoUrl(S3_BASE_URL + u.getPhotoUrl());
            }
        }
        return list;
    }

    public Users addEmployee(Users data) {
        data.setPassword(passwordEncoder.encode(data.getPassword()));
        return userRepository.save(data);
    }

    // ---------------- COUNT LOGIC ----------------
    public long getOnLeaveTodayCount() {
        return leaveRequestsRepository.countLeaveToday(LocalDate.now());
    }

    public long getPresentTodayCount() {
        String today = LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        return recordsRepo.countPresentToday(today);
    }

//    public List<Users> addBulkEmployees(List<Users> users) {
//
//        List<Users> validUsers = new ArrayList<>();
//
//        for (Users u : users) {
//
//            if (u.getEmail() == null || u.getEmail().isBlank()) continue;
//            if (u.getEmpid() == null || u.getEmpid().isBlank()) continue;
//            if (u.getFullName() == null || u.getFullName().isBlank()) continue;
//            if (u.getPassword() == null || u.getPassword().isBlank()) continue;
//
//            boolean emailExists = userRepository.findByEmailOnly(u.getEmail()).isPresent();
//            boolean empIdExists = userRepository.findByEmpid(u.getEmpid()).isPresent();
//
//            if (!emailExists && !empIdExists) {
//                // 🔐 HASH PASSWORD
//                u.setPassword(passwordEncoder.encode(u.getPassword()));
//                validUsers.add(u);
//            }
//        }
//
//        return validUsers.isEmpty() ? new ArrayList<>() : userRepository.saveAll(validUsers);
//    }
public Map<String, Object> addBulkEmployees(List<Users> users) {

    List<Users> toInsert = new ArrayList<>();
    List<Map<String, String>> errors = new ArrayList<>();

    for (Users u : users) {

        // ---------------- REQUIRED FIELDS ----------------
        if (u.getEmpid() == null || u.getEmpid().isBlank()) {
            errors.add(Map.of("error", "EmpID missing"));
            continue;
        }
        if (u.getFullName() == null || u.getFullName().isBlank()) {
            errors.add(Map.of("empid", u.getEmpid(), "error", "Name missing"));
            continue;
        }
        if (u.getEmail() == null || u.getEmail().isBlank()) {
            errors.add(Map.of("empid", u.getEmpid(), "error", "Email missing"));
            continue;
        }
        if (u.getPassword() == null || u.getPassword().isBlank()) {
            errors.add(Map.of("empid", u.getEmpid(), "error", "Password missing"));
            continue;
        }

        // ---------------- DUPLICATES ----------------
        if (userRepository.findByEmailOnly(u.getEmail()).isPresent()) {
            errors.add(Map.of("email", u.getEmail(), "error", "Email already exists"));
            continue;
        }
        if (userRepository.findByEmpid(u.getEmpid()).isPresent()) {
            errors.add(Map.of("empid", u.getEmpid(), "error", "EmpID already exists"));
            continue;
        }

        // ---------------- VALIDATE DATE FORMAT (String) ----------------
        // ---------------- SAFE DATE HANDLING ----------------
        try {

            if (u.getDob() != null && !u.getDob().isBlank()) {

                String dob = u.getDob().trim();

                // If CSV has dd-MM-yyyy, convert it
                if (dob.matches("\\d{2}-\\d{2}-\\d{4}")) {
                    DateTimeFormatter input = DateTimeFormatter.ofPattern("dd-MM-yyyy");
                    DateTimeFormatter output = DateTimeFormatter.ISO_LOCAL_DATE;
                    dob = LocalDate.parse(dob, input).format(output);
                    u.setDob(dob);
                } else {
                    // validates yyyy-MM-dd
                    LocalDate.parse(dob);
                }
            }

            if (u.getJoiningDate() != null && !u.getJoiningDate().isBlank()) {

                String jd = u.getJoiningDate().trim();

                if (jd.matches("\\d{2}-\\d{2}-\\d{4}")) {
                    DateTimeFormatter input = DateTimeFormatter.ofPattern("dd-MM-yyyy");
                    DateTimeFormatter output = DateTimeFormatter.ISO_LOCAL_DATE;
                    jd = LocalDate.parse(jd, input).format(output);
                    u.setJoiningDate(jd);
                } else {
                    LocalDate.parse(jd);
                }
            }

        } catch (Exception e) {
            errors.add(Map.of(
                    "empid", u.getEmpid(),
                    "error", "Invalid date format (expected dd-MM-yyyy or yyyy-MM-dd)"
            ));
            continue;
        }


        // ---------------- HASH PASSWORD ----------------
        u.setPassword(passwordEncoder.encode(u.getPassword()));

        // add for batch insert
        toInsert.add(u);
    }

    // ---------------- SAVE ONLY VALID USERS ----------------
    if (!toInsert.isEmpty()) {
        userRepository.saveAll(toInsert);
    }

    // ---------------- RESPONSE ----------------
    return Map.of(
            "inserted", toInsert.size(),
            "failed", errors.size(),
            "errors", errors
    );
}

    public Users updateEmployeeJobDetails(Integer id, Users data) {
        Users user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (data.getFullName() != null) user.setFullName(data.getFullName());
        if (data.getEmail() != null) user.setEmail(data.getEmail());
        if (data.getDesignation() != null) user.setDesignation(data.getDesignation());
        if (data.getDomain() != null) user.setDomain(data.getDomain());
        if (data.getBaseSalary() != null) user.setBaseSalary(data.getBaseSalary());

        return userRepository.save(user);
    }

    public long getTotalUserCount() {
        return userRepository.count();
    }

    public void sendResetOtp(ForgotPasswordRequest request) {
        Users user = userRepository.findByEmailOnly(request.getEmail())
                .orElseThrow(() -> new RuntimeException("No user found with this email"));

        String otp = String.format("%06d", new Random().nextInt(1_000_000));
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(10);

        user.setResetOtp(otp);
        user.setResetOtpExpiry(expiry);
        userRepository.save(user);

        String mailBody = "Hello " + user.getFullName() + ",\n\n" +
                "Your OTP to reset your password is: " + otp + "\n" +
                "This OTP will expire in 10 minutes.\n\n" +
                "If you did not request, ignore this email.";

        emailService.sendEmail(user.getEmail(), "Password Reset OTP - TeamHub", mailBody);
    }
    public void resetPassword(String email, String otp, String newPassword) {
        Users user = userRepository.findByEmailOnly(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getResetOtp() == null || user.getResetOtpExpiry() == null) {
            throw new RuntimeException("No OTP request found");
        }

        if (!user.getResetOtp().equals(otp)) {
            throw new RuntimeException("Invalid OTP");
        }

        if (user.getResetOtpExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP expired");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetOtp(null);
        user.setResetOtpExpiry(null);
        userRepository.save(user);
    }
    public Map<String, Long> getDepartmentCounts() {
        List<Object[]> results = userRepository.countUsersByDepartment(); // added by venkatasagar

        Map<String, Long> map = new HashMap<>();
        for (Object[] row : results) {
            map.put((String) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    public void deleteuser(Integer userId) {
        userRepository.deleteById(userId);
    }

}

