package com.ietscroll.response;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Email;

public class LostItemResponse {
	private UUID publicIdOfLostRequest;

	private String lostItemname;

	private String imageURLOfItem;

	private String predictedLocation;

	private String description;

	private int prize;
	
	private LocalDateTime createdAt;
	
	@Email
	private String contactTo;

	public UUID getPublicIdOfLostRequest() {
		return publicIdOfLostRequest;
	}

	public void setPublicIdOfLostRequest(UUID publicIdOfLostRequest) {
		this.publicIdOfLostRequest = publicIdOfLostRequest;
	}

	public String getLostItemname() {
		return lostItemname;
	}

	public void setLostItemname(String lostItemname) {
		this.lostItemname = lostItemname;
	}

	public String getImageURLOfItem() {
		return imageURLOfItem;
	}

	public void setImageURLOfItem(String imageURLOfItem) {
		this.imageURLOfItem = imageURLOfItem;
	}

	public String getPredictedLocation() {
		return predictedLocation;
	}

	public void setPredictedLocation(String predictedLocation) {
		this.predictedLocation = predictedLocation;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public int getPrize() {
		return prize;
	}

	public void setPrize(int prize) {
		this.prize = prize;
	}

	@Override
	public String toString() {
		return "LostItemResponse [publicIdOfLostRequest=" + publicIdOfLostRequest + ", lostItemname=" + lostItemname
				+ ", imageURLOfItem=" + imageURLOfItem + ", predictedLocation=" + predictedLocation + ", description="
				+ description + ", prize=" + prize + "]";
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public String getContactTo() {
		return contactTo;
	}

	public void setContactTo(String contactTo) {
		this.contactTo = contactTo;
	}

}
