#ifndef MQTT_CLIENT_H
#define MQTT_CLIENT_H

#include "sensor.h"

void mqtt_init(void (*downlinkHandler)(const char *payload));
void mqtt_loop();
bool mqtt_connected();
bool mqtt_publish(const SensorData &d, const char *msgId);

#endif
