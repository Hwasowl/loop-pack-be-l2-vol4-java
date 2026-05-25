package com.loopers.interfaces.api.auth;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Optional;

@Component
public class LdapAdminArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER_LDAP = "X-Loopers-Ldap";
    public static final String EXPECTED_LDAP = "loopers.admin";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginAdmin.class) && parameter.getParameterType().equals(AdminUser.class);
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        String ldap = Optional.ofNullable(webRequest.getHeader(HEADER_LDAP))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "어드민 인증 헤더가 누락되었습니다."));
        if (!EXPECTED_LDAP.equals(ldap)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "유효하지 않은 어드민 자격입니다.");
        }
        return new AdminUser(ldap);
    }
}
