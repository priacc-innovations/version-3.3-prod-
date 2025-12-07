// src/main/java/com/example/employee_service_mama/service/WalletService.java
package com.example.employee_service_mama.service;

import com.example.employee_service_mama.dto.WalletResponse;
import com.example.employee_service_mama.model.Attendance;
import com.example.employee_service_mama.model.Users;
import com.example.employee_service_mama.model.Wallet;
import com.example.employee_service_mama.repository.AttendanceRepository;
import com.example.employee_service_mama.repository.UserRepository;
import com.example.employee_service_mama.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    public Float getMonthSalary(Integer userId) {
        return walletRepository.monthsalary(userId);
    }

    public Double getDailyRate(Integer userId) {
        return walletRepository.dailyrate(userId);
    }

    public Double getTotalSalary() {
        return walletRepository.totalmonthsalary();
    }

    public Double getNetPayable() {
        return walletRepository.currentMonthEarned();
    }

    public Double getTotalDeduction() {
        return walletRepository.totaldeduction();
    }

    public String addDeduction(String empid, Double deductionAmount) {
        Wallet wallet = walletRepository.findByEmpid(empid).orElse(null);
        if (wallet == null) {
            return "Salary details not found for Employee ID: " + empid;
        }

        double currentDeduction = wallet.getDeduction() == null ? 0.0 : wallet.getDeduction();
        wallet.setDeduction(currentDeduction + deductionAmount);
        walletRepository.save(wallet);

        return "Deduction added successfully for Employee ID: " + empid;
    }

    public Wallet getSalaryDetails(Integer userId) {
        return walletRepository.findByUserId(userId).orElse(null);
    }


    public List<WalletResponse> getAllSalaryResponses() {
        return walletRepository.findAllAsResponse();
    }

    @Scheduled(cron = "0 45 18 * * MON-FRI") // 6:45 PM after attendance finalization
    @Transactional
    public void updateDailySalary() {

        LocalDate today = LocalDate.now();

        List<Attendance> attendanceList = attendanceRepository.findByDate(today);

        for (Attendance att : attendanceList) {

            Users user = att.getUser();
            Wallet wallet = walletRepository.findActiveWalletForUser(user.getId())
                    .orElse(createNewWalletForUser(user));

            double addAmount = 0;

            switch (att.getStatus()) {
                case "PRESENT":
                    addAmount = wallet.getDailyRate();
                    break;

                case "HALF_DAY":
                    addAmount = wallet.getDailyRate() / 2;
                    break;

                case "LEAVE":
                    addAmount = wallet.getDailyRate(); // (If company policy allows)
                    break;

                default:
                    addAmount = 0;
            }

            wallet.setCurrentMonthEarned(wallet.getCurrentMonthEarned() + addAmount);
            wallet.setLastUpdated(OffsetDateTime.now());
            walletRepository.save(wallet);
        }

        System.out.println("✔ Salary updated for " + today);
    }
    @Scheduled(cron = "0 0 0 * * *") // Every midnight
    @Transactional
    public void checkAndCreateNewCycle() {

        LocalDate today = LocalDate.now();

        if (today.getDayOfMonth() != 23) return;

        List<Users> users = userRepository.findAll();

        for (Users user : users) {
            Wallet activeWallet = walletRepository.findActiveWalletForUser(user.getId()).orElse(null);

            if (activeWallet == null) continue;

            // Close existing cycle
            activeWallet.setCycleEnd(today);
            walletRepository.save(activeWallet);

            // Create new cycle starting tomorrow 24th
            Wallet newWallet = Wallet.builder()
                    .user(user)
                    .empid(user.getEmpid())
                    .monthlySalary(activeWallet.getMonthlySalary())
                    .dailyRate(activeWallet.getDailyRate())
                    .currentMonthEarned(0.0)
                    .deduction(0.0)
                    .cycleStart(today.plusDays(1)) // 24th
                    .build();

            walletRepository.save(newWallet);
        }

        System.out.println("💰 New salary cycle created starting tomorrow!");
    }



    private Wallet createNewWalletForUser(Users user) {

        double monthlySalary = user.getBaseSalary() != null ? user.getBaseSalary() : 0.0;
        double dailyRate = monthlySalary / 30.0; // OR divide by working days if needed

        Wallet wallet = Wallet.builder()
                .user(user)
                .empid(user.getEmpid())
                .monthlySalary(monthlySalary)
                .dailyRate(dailyRate)
                .currentMonthEarned(0.0)
                .deduction(0.0)
                .cycleStart(getCurrentCycleStartDate())
                .build();

        return walletRepository.save(wallet);
    }
    private LocalDate getCurrentCycleStartDate() {
        LocalDate today = LocalDate.now();
        return (today.getDayOfMonth() < 24)
                ? today.withDayOfMonth(24).minusMonths(1)
                : today.withDayOfMonth(24);
    }

    public Double deductionamount(Integer userId) {
        return walletRepository.findDeductionByUserId(userId);
    }

}
