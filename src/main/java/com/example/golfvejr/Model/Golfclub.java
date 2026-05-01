package com.example.golfvejr.Model;

import jakarta.persistence.*;

@Entity
public class Golfclub {
    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)

    private Long id;
    private String name;
    private double latitude;
    private double longitude;
    private String street;
    private String city;
    private String website;
    private String phone;

    public Golfclub(String name, double latitude, double longitude){
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Golfclub(Long id, String name, double latitude, double longitude) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Golfclub(){}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
