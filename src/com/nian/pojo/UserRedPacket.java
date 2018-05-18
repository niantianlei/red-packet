package com.nian.pojo;

import java.io.Serializable;
import java.sql.Timestamp;

public class UserRedPacket implements Serializable {

	private Long id;
	private Long redPacketId;
	private Long userId;
	private Double amount;
	private Timestamp grabTime;
	private String note;
	
	private static final long serialVersionUID = -5617482065991830143L;
    
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getRedPacketId() {
		return redPacketId;
	}

	public void setRedPacketId(Long redPacketId) {
		this.redPacketId = redPacketId;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public Double getAmount() {
		return amount;
	}

	public void setAmount(Double amount) {
		this.amount = amount;
	}

	public Timestamp getGrabTime() {
		return grabTime;
	}

	public void setGrabTime(Timestamp grabTime) {
		this.grabTime = grabTime;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}
}