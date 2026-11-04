package com.gmart.entity;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "contact_details")
public class ContactDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer custId;

    private String custName;

    private String custEmail;

    @Column(length = 2000)
    private String custMessage;

    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt = new Date();

    public ContactDetail() {
    }

    public ContactDetail(Integer custId, String custName, String custEmail, String custMessage) {
        this.custId = custId;
        this.custName = custName;
        this.custEmail = custEmail;
        this.custMessage = custMessage;
        this.createdAt = new Date();
    }

    public Integer getCustId() {
        return custId;
    }

    public void setCustId(Integer custId) {
        this.custId = custId;
    }

    public String getCustName() {
        return custName;
    }

    public void setCustName(String custName) {
        this.custName = custName;
    }

    public String getCustEmail() {
        return custEmail;
    }

    public void setCustEmail(String custEmail) {
        this.custEmail = custEmail;
    }

    public String getCustMessage() {
        return custMessage;
    }

    public void setCustMessage(String custMessage) {
        this.custMessage = custMessage;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
