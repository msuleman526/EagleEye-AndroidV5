package com.suleman.eagleeye.models;

import android.os.Parcel;
import android.os.Parcelable;

public class FlightLog implements Parcelable {

    private String started_at;
    private String ended_at;
    private Integer id;
    private String log;

    // No-args constructor (needed for serializers like Gson/Moshi)
    public FlightLog() { }

    // Full constructor
    public FlightLog(String started_at, String ended_at, Integer id) {
        this.started_at = started_at;
        this.ended_at = ended_at;
        this.id = id;
    }

    // Parcelable constructor
    protected FlightLog(Parcel in) {
        started_at = in.readString();
        ended_at = in.readString();
        id = (Integer) in.readValue(Integer.class.getClassLoader());
        log = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(started_at);
        dest.writeString(ended_at);
        dest.writeValue(id);
        dest.writeString(log);
    }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<FlightLog> CREATOR = new Creator<FlightLog>() {
        @Override
        public FlightLog createFromParcel(Parcel in) {
            return new FlightLog(in);
        }

        @Override
        public FlightLog[] newArray(int size) {
            return new FlightLog[size];
        }
    };

    // Getters and Setters
    public String getStarted_at() { return started_at; }
    public void setStarted_at(String started_at) { this.started_at = started_at; }

    public String getEnded_at() { return ended_at; }
    public void setEnded_at(String ended_at) { this.ended_at = ended_at; }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getLog() { return log; }
    public void setLog(String log) { this.log = log; }

    @Override
    public String toString() {
        return "FlightLog{" +
                "started_at='" + started_at + '\'' +
                ", ended_at='" + ended_at + '\'' +
                ", id=" + id +
                ", log='" + log + '\'' +
                '}';
    }
}
