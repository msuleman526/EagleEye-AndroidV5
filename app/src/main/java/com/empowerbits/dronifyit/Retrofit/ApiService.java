package com.empowerbits.dronifyit.Retrofit;

import com.empowerbits.dronifyit.ApiResponse.AddProjectResponse;
import com.empowerbits.dronifyit.ApiResponse.FlightLogResponse;
import com.empowerbits.dronifyit.ApiResponse.LoginResponse;
import com.empowerbits.dronifyit.ApiResponse.ProjectsResponse;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;

public interface ApiService {

    @FormUrlEncoded
    @POST("auth/login")
    Call<LoginResponse> login(@Field("email") String email, @Field("password") String password);

    @FormUrlEncoded
    @POST("projects")
    Call<AddProjectResponse> createProjectInfo(@Header("Authorization") String token, @Field("address") String address, @Field("latitude") String latitude, @Field("longitude") String longitude, @Field("assign_to") int assign_to, @Field("name") String name);

    @FormUrlEncoded
    @PUT("projects/update/{projectId}")
    Call<AddProjectResponse> updateProjectInfo(@Header("Authorization") String token, @Path("projectId") String projectId, @Field("address") String address, @Field("latitude") String latitude, @Field("longitude") String longitude, @Field("name") String name);

    @FormUrlEncoded
    @PUT("projects/update/{projectId}")
    Call<AddProjectResponse> updateWaypoints(@Header("Authorization") String token, @Path("projectId") String projectId, @Field("flight_setting") String flightSettings, @Field("flight_path") String flightPath, @Field("flight_path_type") String flight_path_type, @Field("height_of_house") int height_of_house, @Field("highest_can") int highest_can, @Field("must_height") int must_height);

    @FormUrlEncoded
    @PUT("projects/update/{projectId}")
    Call<AddProjectResponse> updateObstacles(@Header("Authorization") String token, @Path("projectId") String projectId, @Field("height_of_house") String height_of_house, @Field("highest_can") String highest_can, @Field("must_height") String must_height, @Field("obstacle_boundary") String obstacle_boundary);

    @GET("projects/app")
    Call<ProjectsResponse> projects(@Header("Authorization") String token);


    @FormUrlEncoded
    @POST("flight/started/{projectId}")
    Call<FlightLogResponse> saveFlightStartedLog(@Header("Authorization") String token, @Path("projectId") String projectId, @Field("date") String date, @Field("log") String log);

    @FormUrlEncoded
    @POST("flight/ended/{flight}")
    Call<FlightLogResponse> saveFlightEndedLog(@Header("Authorization") String token, @Path("flight") String flight, @Field("date") String date, @Field("log") String log);

    @Multipart
    @POST("projects/upload/{projectId}")
    Call<Response<String>> uploadMediaImage(@Header("Authorization") String token, @Path("projectId") String projectId, @Part MultipartBody.Part image, @Part("completed") RequestBody completed);

}