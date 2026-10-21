package com.platform.uop.monitor.converter;

import com.google.protobuf.InvalidProtocolBufferException;
import com.platform.uop.proto.v1.MonitorDescriptor;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class MonitorDescriptorConverter
    implements AttributeConverter<MonitorDescriptor, byte[]> {

  @Override
  public byte[] convertToDatabaseColumn(MonitorDescriptor descriptor) {
    return descriptor == null ? null : descriptor.toByteArray();
  }

  @Override
  public MonitorDescriptor convertToEntityAttribute(byte[] bytes) {
    if (bytes == null) {
      return null;
    }

    try {
      return MonitorDescriptor.parseFrom(bytes);
    } catch (InvalidProtocolBufferException exception) {
      throw new IllegalArgumentException(
          "Failed to deserialize monitor descriptor", exception);
    }
  }
}
