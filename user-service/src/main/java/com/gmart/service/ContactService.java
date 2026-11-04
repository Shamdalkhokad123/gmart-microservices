package com.gmart.service;

import com.gmart.entity.ContactDetail;
import com.gmart.repository.ContactDetailRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContactService {

    @Autowired
    private ContactDetailRepository contactDetailRepository;

    public ContactDetail saveContact(ContactDetail contact) {
        return contactDetailRepository.save(contact);
    }

    public List<ContactDetail> getAllInquiries() {
        return contactDetailRepository.findAllByOrderByCreatedAtDesc();
    }
}
