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

    @Autowired
    private com.gmart.web.RateLimiter limiter;

    /** Public form: only these fields are accepted. "website" is a hidden honeypot field that people never fill in. */
    public record ContactForm(
            @jakarta.validation.constraints.NotBlank(message = "Please enter your name")
            @jakarta.validation.constraints.Size(max = 100, message = "Name is too long") String custName,
            @jakarta.validation.constraints.NotBlank(message = "Please enter your email")
            @jakarta.validation.constraints.Email(message = "Please enter a valid email") String custEmail,
            @jakarta.validation.constraints.NotBlank(message = "Please enter a message")
            @jakarta.validation.constraints.Size(max = 2000, message = "Message is too long (2000 characters max)") String custMessage,
            String website) {
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> submitMessage(@jakarta.validation.Valid @RequestBody ContactForm form,
                                                           jakarta.servlet.http.HttpServletRequest http) {
        String thanks = "Thank you! Your message has been received.";
        if (form.website() != null && !form.website().isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(thanks)); // bot: pretend it worked
        }
        try {
            limiter.acquire("contact-ip:" + com.gmart.web.RateLimiter.clientIp(http), 5, java.time.Duration.ofHours(1),
                    "You've sent several messages already. Please wait a while or email us directly.");
            // A new record every time: ids and dates are never taken from the request
            ContactDetail contact = new ContactDetail();
            contact.setCustName(form.custName().trim());
            contact.setCustEmail(form.custEmail().trim());
            contact.setCustMessage(form.custMessage().trim());
            contactService.saveContact(contact);
            return ResponseEntity.ok(ApiResponse.success(thanks));
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
