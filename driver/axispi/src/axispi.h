/**
 * @file axispi.h
 * @author wlanxww (xueweiwujxw@outlook.com)
 * @brief
 * @date 2024-12-20
 *
 * @copyright Copyright (c) 2024
 *
 */

#pragma once

#include "types.h"

#define AXI_SPI_CMD_RW (0x01 << 24)
#define AXI_SPI_CMD_WRITE (0x00 << 24)
#define AXI_SPI_CMD_ENABLE (0x11 << 24)
#define AXI_SPI_CMD_DISABLE (0x10 << 24)

#define AXI_SPI_STATUS_CMD_INT_ENABLE (1 << 0)
#define AXI_SPI_STATUS_RSP_INT_ENABLE (1 << 1)
#define AXI_SPI_STATUS_CMD_INT_FLAG (1 << 8)
#define AXI_SPI_STATUS_RSP_INT_FLAG (1 << 9)

#define AXI_SPI_MODE_CPOL (1 << 0)
#define AXI_SPI_MODE_CPHA (1 << 1)

#define AXI_SPI_MAX_DEVICES 8

typedef struct axispi_reg
{
    u32 data;
    u32 status;
    u32 config;
    u32 sclk_toggle;
    u32 ss_setup;
    u32 ss_hold;
    u32 ss_disable;
    u32 cmd_pop_cond;
    u32 ss_signal;
} axispi_reg_t;

typedef struct axispi_config
{
    u8 cpol;         // 空闲时钟极性，0 低电平 1高电平
    u8 cpha;         // 采样时钟沿，0 上升沿采样，下降沿移位，1上升沿采样，下降沿移位
    u32 sclk_toggle; // 时钟分频，sclk = FCLK / (2 * (sclk_toggle + 1))
    u32 ss_setup;    // 片选有效到下一个字节传输前的时钟周期数
    u32 ss_hold;     // 最后一个字节传输完成到片段无效的时钟周期数
    u32 ss_disable;  // 片选无效到片选有效的时钟周期数
    u16 timout_ms;   // 操作超时时间
    u8 continuous;   // 连续模式 0 默认模式 1 连续模式
    u8 ss_width;     // 片选信号位宽
} axispi_config_t;

typedef struct axispi_ctrl
{
    struct axispi_reg *reg;
    u16 timout_ms;
    u8 continuous;
    u8 ss_width;
} axispi_ctrl_t;

struct axispi_ctrl *axispi_ctrl_create(void *baseaddr);

void axispi_ctrl_free(struct axispi_ctrl *ctrl);

int axispi_ctrl_init(struct axispi_ctrl *ctrl, const struct axispi_config config);

int axispi_write(struct axispi_ctrl *ctrl, u8 data);

int axispi_read_request(struct axispi_ctrl *ctrl);

int axispi_read_write_request(struct axispi_ctrl *ctrl, u8 data);

int axispi_read(struct axispi_ctrl *ctrl);

int axispi_select(struct axispi_ctrl *ctrl, u8 index);

int axispi_diselect(struct axispi_ctrl *ctrl, u8 index);

int axispi_on(struct axispi_ctrl *ctrl);

int axispi_off(struct axispi_ctrl *ctrl);
