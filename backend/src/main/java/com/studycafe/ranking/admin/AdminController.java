package com.studycafe.ranking.admin;

import com.studycafe.ranking.admin.dto.AdminDtos.AdminSchool;
import com.studycafe.ranking.admin.dto.AdminDtos.AdminUser;
import com.studycafe.ranking.admin.dto.AdminDtos.BatchResult;
import com.studycafe.ranking.admin.dto.AdminDtos.CafeQr;
import com.studycafe.ranking.admin.dto.AdminDtos.RoleUpdateRequest;
import com.studycafe.ranking.admin.dto.AdminDtos.SchoolRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 전용 API. 접근 제어는 SecurityConfig(/api/admin/** = ROLE_ADMIN). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ----- 사용자 -----

    @GetMapping("/users")
    public List<AdminUser> users() {
        return adminService.listUsers();
    }

    @PutMapping("/users/{id}/role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeRole(@AuthenticationPrincipal Long adminUserId,
                           @PathVariable Long id,
                           @Valid @RequestBody RoleUpdateRequest req) {
        adminService.changeRole(adminUserId, id, req.role());
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@AuthenticationPrincipal Long adminUserId, @PathVariable Long id) {
        adminService.deleteUser(adminUserId, id);
    }

    @PostMapping("/users/{id}/warnings/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetWarnings(@PathVariable Long id) {
        adminService.resetWarnings(id);
    }

    @PostMapping("/users/{id}/force-checkout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forceCheckout(@PathVariable Long id) {
        adminService.forceCheckout(id);
    }

    // ----- 학교 -----

    @GetMapping("/schools")
    public List<AdminSchool> schools() {
        return adminService.listSchools();
    }

    @PostMapping("/schools")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminSchool createSchool(@Valid @RequestBody SchoolRequest req) {
        return adminService.createSchool(req);
    }

    @PutMapping("/schools/{id}")
    public AdminSchool updateSchool(@PathVariable Long id, @Valid @RequestBody SchoolRequest req) {
        return adminService.updateSchool(id, req);
    }

    @DeleteMapping("/schools/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSchool(@PathVariable Long id) {
        adminService.deleteSchool(id);
    }

    // ----- 카페 QR -----

    @GetMapping("/cafes")
    public List<CafeQr> cafes() {
        return adminService.listCafes();
    }

    @PostMapping("/cafes/{id}/rotate-qr")
    public CafeQr rotateQr(@PathVariable Long id) {
        return adminService.rotateCafeQr(id);
    }

    // ----- 배치 -----

    @PostMapping("/batch/daily-close")
    public BatchResult runDailyClose() {
        return adminService.runDailyClose();
    }
}
