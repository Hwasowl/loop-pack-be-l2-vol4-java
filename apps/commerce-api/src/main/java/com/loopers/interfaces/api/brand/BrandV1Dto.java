package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;

public class BrandV1Dto {

    public record Response(Long id, String name, String description) {
        public static Response from(BrandInfo info) {
            return new Response(info.id(), info.name(), info.description());
        }
    }
}
