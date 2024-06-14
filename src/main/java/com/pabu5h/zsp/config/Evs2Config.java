package com.pabu5h.zsp.config;

import com.pabu5h.zsp.job.JobDto;
import org.pabuff.evs2helper.email.EmailService;
import org.pabuff.evs2helper.locale.LocalHelper;
import org.pabuff.evs2helper.meter_usage.MeterUsageProcessor;
import org.pabuff.evs2helper.scope.ScopeHelper;
import org.pabuff.oqghelper.OqgHelper;
import org.pabuff.oqghelper.QueryHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;


@Configuration
public class Evs2Config {
    @Bean
    public OqgHelper oqgHelper() {
        return new OqgHelper();
    }


    @Bean
    public MeterUsageProcessor meterUsageProcessor() {
        return new MeterUsageProcessor();
    }

    @Bean
    public QueryHelper queryHelper() {
        return new QueryHelper();
    }

    @Bean
    public ScopeHelper scopeHelper() {
        return new ScopeHelper();
    }

    @Bean
    public LocalHelper localHelper() {
        return new LocalHelper();
    }

    @Bean
    public JobDto jobDto() {
        return new JobDto();
    }

//    @Bean
//    public EmailService emailService() {
//        return new EmailService();
//    }
//
//    @Bean
//    public JavaMailSender javaMailSender() {
//        return new JavaMailSender();
//    }


}
