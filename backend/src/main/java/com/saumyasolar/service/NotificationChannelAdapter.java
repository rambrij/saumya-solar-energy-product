package com.saumyasolar.service;
import com.saumyasolar.domain.NotificationDelivery;
public interface NotificationChannelAdapter { boolean supports(String channel); String providerCode(); void dispatch(NotificationDelivery delivery) throws Exception; }