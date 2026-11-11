package com.platform.uop.unit.monitor.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.uop.monitor.converter.MonitorDescriptorConverter;
import com.platform.uop.proto.v1.MonitorDescriptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MonitorDescriptorConverterTest {

  private static MonitorDescriptor descriptor() {
    return MonitorDescriptor.newBuilder()
        .setId("desc-1")
        .setName("Test Monitor")
        .setDescription("A test monitor")
        .setVersion("1.0")
        .setActive(true)
        .build();
  }

  @Test
  @DisplayName("Should convert descriptor to byte array")
  void convertToDatabaseColumn_Serializes() throws Exception {
    MonitorDescriptorConverter converter = new MonitorDescriptorConverter();
    MonitorDescriptor descriptor = descriptor();

    byte[] bytes = converter.convertToDatabaseColumn(descriptor);

    assertThat(bytes).isEqualTo(descriptor.toByteArray());
    MonitorDescriptor parsed = MonitorDescriptor.parseFrom(bytes);
    assertThat(parsed.getId()).isEqualTo("desc-1");
    assertThat(parsed.getName()).isEqualTo("Test Monitor");
    assertThat(parsed.getActive()).isTrue();
  }

  @Test
  @DisplayName("Should convert null descriptor to null bytes")
  void convertToDatabaseColumn_Null_ReturnsNull() {
    MonitorDescriptorConverter converter = new MonitorDescriptorConverter();

    assertThat(converter.convertToDatabaseColumn(null)).isNull();
  }

  @Test
  @DisplayName("Should convert byte array back to descriptor")
  void convertToEntityAttribute_Deserializes() throws Exception {
    MonitorDescriptorConverter converter = new MonitorDescriptorConverter();
    byte[] bytes = descriptor().toByteArray();

    MonitorDescriptor result = converter.convertToEntityAttribute(bytes);

    assertThat(result.getId()).isEqualTo("desc-1");
    assertThat(result.getName()).isEqualTo("Test Monitor");
    assertThat(result.getActive()).isTrue();
  }

  @Test
  @DisplayName("Should convert null bytes to null descriptor")
  void convertToEntityAttribute_Null_ReturnsNull() {
    MonitorDescriptorConverter converter = new MonitorDescriptorConverter();

    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException for invalid proto bytes")
  void convertToEntityAttribute_InvalidBytes_Throws() {
    MonitorDescriptorConverter converter = new MonitorDescriptorConverter();

    assertThatThrownBy(() -> converter.convertToEntityAttribute(new byte[] { 1, 2, 3 }))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to deserialize monitor descriptor");
  }

  @Test
  @DisplayName("Should convert empty byte array to a default descriptor")
  void convertToEntityAttribute_EmptyBytes_ReturnsDefault() throws Exception {
    MonitorDescriptorConverter converter = new MonitorDescriptorConverter();

    MonitorDescriptor result = converter.convertToEntityAttribute(new byte[0]);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEmpty();
  }
}
