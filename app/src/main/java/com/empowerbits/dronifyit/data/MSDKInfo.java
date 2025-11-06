package com.empowerbits.dronifyit.data;

import dji.sdk.keyvalue.value.product.ProductType;

/**
 * MSDK Information data class - Java equivalent of MSDKInfo.kt
 * Contains SDK version, product information, and connection state data
 * 
 * @author Suleman
 * @date 2025-06-26
 */
public class MSDKInfo {
    
    public static final String DEFAULT_STR = "N/A";
    public static final String NO_NETWORK_STR = "No Network";
    public static final String ONLINE_STR = "Online";
    
    // SDK Information
    private String SDKVersion;
    private String buildVer;
    private boolean isDebug;
    private String packageProductCategory;
    private String coreInfo;
    
    // Product Information
    private ProductType productType;
    private String firmwareVer;
    
    // Connection Information
    private String networkInfo;
    private String countryCode;
    
    // LDM Information
    private String isLDMEnabled;
    private String isLDMLicenseLoaded;
    
    public MSDKInfo() {
        this.SDKVersion = DEFAULT_STR;
        this.buildVer = DEFAULT_STR;
        this.isDebug = false;
        this.packageProductCategory = DEFAULT_STR;
        this.coreInfo = DEFAULT_STR;
        this.productType = ProductType.UNKNOWN;
        this.firmwareVer = DEFAULT_STR;
        this.networkInfo = NO_NETWORK_STR;
        this.countryCode = DEFAULT_STR;
        this.isLDMEnabled = "false";
        this.isLDMLicenseLoaded = "false";
    }
    
    public MSDKInfo(String SDKVersion) {
        this();
        this.SDKVersion = SDKVersion;
    }
    
    // Getters and Setters
    public String getSDKVersion() {
        return SDKVersion;
    }
    
    public void setSDKVersion(String SDKVersion) {
        this.SDKVersion = SDKVersion;
    }
    
    public String getBuildVer() {
        return buildVer;
    }
    
    public void setBuildVer(String buildVer) {
        this.buildVer = buildVer;
    }
    
    public boolean isDebug() {
        return isDebug;
    }
    
    public void setDebug(boolean debug) {
        isDebug = debug;
    }
    
    public String getPackageProductCategory() {
        return packageProductCategory;
    }
    
    public void setPackageProductCategory(String packageProductCategory) {
        this.packageProductCategory = packageProductCategory;
    }
    
    public String getCoreInfo() {
        return coreInfo;
    }
    
    public void setCoreInfo(String coreInfo) {
        this.coreInfo = coreInfo;
    }
    
    public ProductType getProductType() {
        return productType;
    }
    
    public void setProductType(ProductType productType) {
        this.productType = productType;
    }
    
    public String getFirmwareVer() {
        return firmwareVer;
    }
    
    public void setFirmwareVer(String firmwareVer) {
        this.firmwareVer = firmwareVer;
    }
    
    public String getNetworkInfo() {
        return networkInfo;
    }
    
    public void setNetworkInfo(String networkInfo) {
        this.networkInfo = networkInfo;
    }
    
    public String getCountryCode() {
        return countryCode;
    }
    
    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }
    
    public String getIsLDMEnabled() {
        return isLDMEnabled;
    }
    
    public void setIsLDMEnabled(String isLDMEnabled) {
        this.isLDMEnabled = isLDMEnabled;
    }
    
    public String getIsLDMLicenseLoaded() {
        return isLDMLicenseLoaded;
    }
    
    public void setIsLDMLicenseLoaded(String isLDMLicenseLoaded) {
        this.isLDMLicenseLoaded = isLDMLicenseLoaded;
    }
    
    @Override
    public String toString() {
        return "MSDKInfo{" +
                "SDKVersion='" + SDKVersion + '\'' +
                ", buildVer='" + buildVer + '\'' +
                ", isDebug=" + isDebug +
                ", packageProductCategory='" + packageProductCategory + '\'' +
                ", productType=" + productType +
                ", firmwareVer='" + firmwareVer + '\'' +
                ", networkInfo='" + networkInfo + '\'' +
                ", countryCode='" + countryCode + '\'' +
                ", isLDMEnabled='" + isLDMEnabled + '\'' +
                ", isLDMLicenseLoaded='" + isLDMLicenseLoaded + '\'' +
                '}';
    }
}