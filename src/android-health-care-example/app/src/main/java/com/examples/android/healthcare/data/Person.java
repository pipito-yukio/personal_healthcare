package com.examples.android.healthcare.data;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

public class Person {
    private final Integer id;
    private final String email; // Required: 検索キー(UNIQUE)
    private final String name;

    public Person(@Nullable Integer id, String email, @Nullable String name) {
        this.id = id;
        this.email = email;
        this.name = name;
    }

    public Person(String email) {
        this.id = null;
        this.email = email;
        this.name = null;
    }
    public Integer getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }

    @Override
    public String toString() {
        return "Person{id=" + id + ", email='" + email + ", name='" + name + '}';
    }
}
