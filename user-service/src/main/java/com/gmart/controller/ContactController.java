package com.gmart.controller;

import com.gmart.web.Errors;
import com.gmart.dto.ApiResponse;
import com.gmart.entity.ContactDetail;
import com.gmart.service.ContactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/contact")
public class ContactController {

    @Autowired
    private ContactService contactService;

    @PostMapping
    public ResponseEntity<ApiResponse<ContactDetail>> submitMessage(@RequestBody ContactDetail contact) {
        try {
            ContactDetail saved = contactService.saveContact(contact);
            return ResponseEntity.ok(ApiResponse.success("Thank you! Your message has been received.", saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(Errors.userMessage(e)));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<ContactDetail>>> getAllMessages() {
        List<ContactDetail> list = contactService.getAllInquiries();
        return ResponseEntity.ok(ApiResponse.success("Inquiries retrieved", list));
    }
}
