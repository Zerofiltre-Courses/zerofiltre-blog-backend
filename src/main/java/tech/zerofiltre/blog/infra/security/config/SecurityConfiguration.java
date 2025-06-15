package tech.zerofiltre.blog.infra.security.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import tech.zerofiltre.blog.domain.metrics.MetricsProvider;
import tech.zerofiltre.blog.domain.user.JwtTokenProvider;
import tech.zerofiltre.blog.domain.user.UserProvider;
import tech.zerofiltre.blog.domain.user.VerificationTokenProvider;
import tech.zerofiltre.blog.infra.entrypoints.rest.SecurityContextManager;
import tech.zerofiltre.blog.infra.providers.api.github.GithubLoginProvider;
import tech.zerofiltre.blog.infra.providers.api.so.StackOverflowLoginProvider;
import tech.zerofiltre.blog.infra.security.filter.*;
import tech.zerofiltre.blog.infra.security.model.GithubAuthenticationTokenProperties;
import tech.zerofiltre.blog.infra.security.model.JwtAuthenticationTokenProperties;
import tech.zerofiltre.blog.infra.security.model.StackOverflowAuthenticationTokenProperties;

@Slf4j
@Configuration
@EnableWebSecurity // Enable security config. This annotation denotes config for spring security.
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationTokenProperties jwTokenConfiguration;
    private final LoginFirstAuthenticationEntryPoint loginFirstAuthenticationEntryPoint;
    private final RoleRequiredAccessDeniedHandler roleRequiredAccessDeniedHandler;
    private final PasswordEncoder passwordEncoder;
    private final StackOverflowAuthenticationTokenProperties stackOverflowTokenConfiguration;
    private final GithubAuthenticationTokenProperties githubTokenConfiguration;
    private final StackOverflowLoginProvider stackOverflowLoginProvider;
    private final GithubLoginProvider githubLoginProvider;
    private final UserProvider userProvider;
    private final VerificationTokenProvider verificationTokenProvider;
    private final JwtTokenProvider jwtTokenProvider;
    private final Jackson2ObjectMapperBuilder objectMapperBuilder;
    private final MetricsProvider metricsProvider;
    private final SecurityContextManager securityContextManager;

    public SecurityConfiguration(
            UserDetailsService userDetailsService,
            JwtAuthenticationTokenProperties jwTokenConfiguration,
            LoginFirstAuthenticationEntryPoint loginFirstAuthenticationEntryPoint,
            RoleRequiredAccessDeniedHandler roleRequiredAccessDeniedHandler,
            PasswordEncoder passwordEncoder,
            StackOverflowAuthenticationTokenProperties stackOverflowTokenConfiguration,
            GithubAuthenticationTokenProperties githubTokenConfiguration, StackOverflowLoginProvider stackOverflowLoginProvider,
            GithubLoginProvider githubLoginProvider, UserProvider userProvider, VerificationTokenProvider verificationTokenProvider, JwtTokenProvider jwtTokenProvider, Jackson2ObjectMapperBuilder objectMapperBuilder, MetricsProvider metricsProvider, SecurityContextManager securityContextManager) {

        this.userDetailsService = userDetailsService;
        this.jwTokenConfiguration = jwTokenConfiguration;
        this.loginFirstAuthenticationEntryPoint = loginFirstAuthenticationEntryPoint;
        this.roleRequiredAccessDeniedHandler = roleRequiredAccessDeniedHandler;
        this.passwordEncoder = passwordEncoder;
        this.stackOverflowTokenConfiguration = stackOverflowTokenConfiguration;
        this.githubTokenConfiguration = githubTokenConfiguration;
        this.stackOverflowLoginProvider = stackOverflowLoginProvider;
        this.githubLoginProvider = githubLoginProvider;
        this.userProvider = userProvider;
        this.verificationTokenProvider = verificationTokenProvider;
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapperBuilder = objectMapperBuilder;
        this.metricsProvider = metricsProvider;
        this.securityContextManager = securityContextManager;
    }

    @Override
    protected void configure(final AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder);
    }


    @Override
    protected void configure(final HttpSecurity http) throws Exception {
        http
                //consider cors configurations
                .cors()
                .and()
                .csrf().disable()
                // make sure we use stateless session; session won't be used to store user's state.
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)

                .and()
                // handle an unauthenticated attempt
                .exceptionHandling().defaultAuthenticationEntryPointFor(loginFirstAuthenticationEntryPoint, (new AntPathRequestMatcher("/**")))
                .and()
                //TODO CUSTOMIZE BAD CREDENTIALS MESSAGES using badCredentialsAuthenticationEntryPoint or Create and ErrorHandlerController to handle /error
                // handle an authorized attempt
                .exceptionHandling().accessDeniedHandler(roleRequiredAccessDeniedHandler)
                .and()
                // Add a filter to validate user credentials and add token in the response header

                // What's the authenticationManager()?
                // An object provided by WebSecurityConfigurerAdapter, used to authenticate the user passing user's credentials
                // The filter needs this auth manager to authenticate the user.
                .addFilter(new JwtUsernameAndPasswordAuthenticationFilter(
                        authenticationManager(),
                        jwTokenConfiguration,
                        verificationTokenProvider,
                        jwtTokenProvider,
                        objectMapperBuilder,
                        userProvider)

                )
                // Add a filter to validate the tokens with every request
                .addFilterAfter(new JwtTokenAuthenticationCheckerFilter(jwTokenConfiguration), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new StackOverflowAuthenticationCheckerFilter(stackOverflowTokenConfiguration, new SocialTokenValidatorAndAuthenticator(stackOverflowLoginProvider, userProvider, metricsProvider, securityContextManager)), JwtTokenAuthenticationCheckerFilter.class)
                .addFilterAfter(new GithubAuthenticationCheckerFilter(githubTokenConfiguration, new SocialTokenValidatorAndAuthenticator(githubLoginProvider, userProvider, metricsProvider, securityContextManager)), StackOverflowAuthenticationCheckerFilter.class)
                .authorizeRequests()
                // allow some specific request to access without being authenticated
                .antMatchers(HttpMethod.POST,
                        "/auth",
                        jwTokenConfiguration.getUri(),
                        "/user",
                        "/user/savePasswordReset",
                        "/user/initPasswordReset",
                        "/notification/help",
                        "/payment/checkout",
                        "/payment/webhook",
                        "/user/github/**").permitAll()
                .antMatchers(HttpMethod.GET,
                        "/article/**", "/course/**", "/chapter/**", "/section/**", "/checkout", "/lesson/*",
                        "/user/registrationConfirm",
                        "/user/resendRegistrationConfirm",
                        "/user/initPasswordReset",
                        "/user/verifyTokenForPasswordReset",
                        "/tag/**",
                        "/search",
                        "/user/profile/*",
                        "/user/jwt/refreshToken",
                        "/payment/success",
                        "/payment/cancel",
                        "/user/github/*",
                        "/reviews/**",
                        "/enrollment/provision",
                        "/tips").permitAll()
                .antMatchers("/anonymous*").anonymous()
                .antMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                .antMatchers("/actuator/health").permitAll()
                .antMatchers("/actuator/prometheus").permitAll()
                // must be an admin if trying to access admin area (authentication is also required here)
                .antMatchers("/actuator/**").hasRole("ADMIN")
                .antMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated();

    }
}


