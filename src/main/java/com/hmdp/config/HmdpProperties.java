package com.hmdp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hmdp")
public class HmdpProperties {
    private Upload upload = new Upload();

    public Upload getUpload() {
        return upload;
    }

    public void setUpload(Upload upload) {
        this.upload = upload;
    }

    public static class Upload {
        private String imageDir = "./data/hmdp/imgs";

        public String getImageDir() {
            return imageDir;
        }

        public void setImageDir(String imageDir) {
            this.imageDir = imageDir;
        }
    }
}
