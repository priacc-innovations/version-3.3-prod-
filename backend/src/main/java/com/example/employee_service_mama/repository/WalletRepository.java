// src/main/java/com/example/employee_service_mama/repository/WalletRepository.java
package com.example.employee_service_mama.repository;

import com.example.employee_service_mama.dto.WalletResponse;
import com.example.employee_service_mama.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Integer> {

    @Query("SELECT w.monthlySalary FROM Wallet w WHERE w.user.id = :userId")
    Float monthsalary(@Param("userId") Integer userId);

    @Query("SELECT w.dailyRate FROM Wallet w WHERE w.user.id = :userId")
    Double dailyrate(@Param("userId") Integer userId);

    @Query("SELECT SUM(w.monthlySalary) FROM Wallet w")
    Double totalmonthsalary();

    @Query("SELECT SUM(w.currentMonthEarned) FROM Wallet w")
    Double currentMonthEarned();

    @Query("SELECT SUM(w.deduction) FROM Wallet w")
    Double totaldeduction();

    Optional<Wallet> findByEmpid(String empid);

    @Query("SELECT w FROM Wallet w WHERE w.user.id = :userId")
    Optional<Wallet> findByUserId(@Param("userId") Integer userId);

    // u.role will be mapped to 'department' in WalletResponse
    @Query("""
           SELECT new com.example.employee_service_mama.dto.WalletResponse(
               u.id,
               w.empid,
               u.fullName,
               u.role,
               w.monthlySalary,
               w.dailyRate,
               w.currentMonthEarned,
               COALESCE(w.deduction, 0)
           )
           FROM Wallet w
           JOIN w.user u
           """)
    List<WalletResponse> findAllAsResponse();

    @Query("SELECT w FROM Wallet w WHERE w.user.id = :userId AND w.cycleEnd IS NULL")
    Optional<Wallet> findActiveWalletForUser(@Param("userId") Integer userId);

    @Query("SELECT w.deduction FROM Wallet w WHERE w.user.id = :userId")
    Double findDeductionByUserId(Integer userId);
}
