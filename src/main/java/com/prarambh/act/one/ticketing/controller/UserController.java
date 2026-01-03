package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.User;
import com.prarambh.act.one.ticketing.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserService service;

    @Value("${actone.admin.purge-password:}")
    private String adminPassword;

    public UserController(UserService service) { this.service = service; }

    private boolean isAdmin(String pass){
        return org.springframework.util.StringUtils.hasText(adminPassword)
                && org.springframework.util.StringUtils.hasText(pass)
                && pass.equals(adminPassword);
    }

    @PostMapping("/api/users")
    public ResponseEntity<?> create(@RequestBody User u, @RequestHeader(name = "X-Admin-Password", required = false) String pass){
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        try{ return ResponseEntity.status(HttpStatus.CREATED).body(service.createUser(u)); }
        catch(IllegalArgumentException e){ return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @PutMapping("/api/users/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody User u, @RequestHeader(name = "X-Admin-Password", required = false) String pass){
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        try{ return ResponseEntity.ok(service.updateUser(id, u)); } catch(IllegalArgumentException e){ return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @GetMapping("/api/users")
    public ResponseEntity<?> listAll(@RequestHeader(name = "X-Admin-Password", required = false) String pass){
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        return ResponseEntity.ok(service.listAll());
    }

    @GetMapping("/api/users/{userId}")
    public ResponseEntity<?> getByUserId(@PathVariable String userId, @RequestHeader(name = "X-Admin-Password", required = false) String pass){
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        User u = service.findByUserId(userId);
        if (u==null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(u);
    }

    @GetMapping("/api/users/by-phone")
    public ResponseEntity<?> byPhone(@RequestParam String phoneNumber, @RequestHeader(name = "X-Admin-Password", required = false) String pass){
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        return ResponseEntity.ok(service.findByPhone(phoneNumber)); }

    @GetMapping("/api/users/by-name")
    public ResponseEntity<?> byName(@RequestParam String fullName, @RequestHeader(name = "X-Admin-Password", required = false) String pass){
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        return ResponseEntity.ok(service.findByName(fullName)); }

    @GetMapping("/api/users/by-email")
    public ResponseEntity<?> byEmail(@RequestParam String email, @RequestHeader(name = "X-Admin-Password", required = false) String pass){
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        return ResponseEntity.ok(service.findByEmail(email)); }

    @DeleteMapping("/api/users/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, @RequestHeader(name = "X-Admin-Password", required = false) String pass){
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        service.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
