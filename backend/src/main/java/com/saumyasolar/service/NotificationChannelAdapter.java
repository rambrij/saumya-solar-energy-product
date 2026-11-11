package com.saumyasolar.service;
import com.saumyasolar.domain.NotificationDelivery;
public interface NotificationChannelAdapter { String channel(); String providerCode(); void dispatch(NotificationDelivery delivery) throws Exception; }