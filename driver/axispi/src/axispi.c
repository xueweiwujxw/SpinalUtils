/**
 * @file axispi.c
 * @author wlanxww (xueweiwujxw@outlook.com)
 * @brief
 * @date 2024-12-20
 *
 * @copyright Copyright (c) 2024
 *
 */

#include <stdlib.h>
#include <errno.h>
#include <unistd.h>
#include <time.h>
#include <stdio.h>

#include "axispi.h"

typedef int (*wait_for_func_t)(struct axispi_ctrl *);

static int axispi_wait(struct axispi_ctrl *ctrl, u16 timeout_ms, wait_for_func_t wait_for) {
    if (ctrl == NULL)
        return -ENODEV;

    if (timeout_ms == 0)
        return 0;

    struct timespec ts = {0, 1000 * 1000};

    while (timeout_ms--) {
        if (wait_for(ctrl) == 0)
            return 0;

        if (timeout_ms < 10)
            ts.tv_nsec = 100 * 1000;

        nanosleep(&ts, NULL);
    }
    return -ETIMEDOUT;
}

/**
 * @brief 判断控制是否可以发送指令
 *
 * @param ctrl 控制器指针
 * @return int 0 正确 <0 errno
 */
static int axispi_cmd_available(struct axispi_ctrl *ctrl) {
    if (ctrl == NULL)
        return -ENODEV;

    u32 status = ctrl->reg->status;
    u32 tx_aval = (status & 0xffff0000) >> 16;

    if (tx_aval > 0)
        return 0;
    else
        return -EBUSY;
}

/**
 * @brief 判断是否可以读取数据
 *
 * @param ctrl 控制器指针
 * @return int 0 正确 <0 errno
 */
static int axispi_rsp_available(struct axispi_ctrl *ctrl) {
    if (ctrl == NULL)
        return -ENODEV;

    u32 status = ctrl->reg->status;
    u32 cmd_rsp_enable = status & AXI_SPI_STATUS_RSP_INT_ENABLE;
    u32 cmd_rsp_flag = status & AXI_SPI_STATUS_RSP_INT_FLAG;

    if (cmd_rsp_enable)
        if (cmd_rsp_flag)
            return 0;
        else
            return -EBUSY;
    else
        return -EINVAL;
}

/**
 * @brief 判断是否可以退出连续模式
 *
 * @param ctrl 控制器指针
 * @return int 0 正确 <0 errno
 */
static int axispi_cancel_continuous_available(struct axispi_ctrl *ctrl) {
    if (ctrl == NULL)
        return -ENODEV;

    u32 ss_signal = ctrl->reg->ss_signal;
    if (ss_signal != GENMASK(ctrl->ss_width - 1, 0))
        return -EBUSY;
    return 0;
}

/**
 * @brief 创建axispi控制设备
 *
 * @param baseaddr 映射基址
 * @return struct axispi_ctrl*
 */
struct axispi_ctrl *axispi_ctrl_create(void *baseaddr) {
    struct axispi_ctrl *ctrl = (struct axispi_ctrl *)malloc(sizeof(struct axispi_ctrl));
    if (ctrl == NULL)
        return NULL;
    ctrl->reg = (struct axispi_reg *)baseaddr;
    return ctrl;
}

/**
 * @brief 释放控制器分配的内存
 *
 * @param ctrl 控制器指针
 */
void axispi_ctrl_free(struct axispi_ctrl *ctrl) {
    if (ctrl != NULL)
        free(ctrl);
}

/**
 * @brief 配置spi属性
 *
 * @param ctrl 控制器指针
 * @param config spi配置
 * @return int 0 正确 <0 errno
 */
int axispi_ctrl_init(struct axispi_ctrl *ctrl, const struct axispi_config config) {
    if (ctrl == NULL)
        return -ENODEV;

    ctrl->reg->config = (config.cpol & AXI_SPI_MODE_CPOL) | ((config.cpha << 1) & AXI_SPI_MODE_CPHA);
    ctrl->reg->status = (AXI_SPI_STATUS_CMD_INT_ENABLE) | (AXI_SPI_STATUS_RSP_INT_ENABLE);
    ctrl->reg->sclk_toggle = config.sclk_toggle;
    ctrl->reg->ss_setup = config.ss_setup;
    ctrl->reg->ss_hold = config.ss_hold;
    ctrl->reg->ss_disable = config.ss_disable;
    ctrl->timout_ms = config.timout_ms;
    ctrl->continuous = config.continuous;
    ctrl->ss_width = config.ss_width;
    return 0;
}

/**
 * @brief 写入单字节数据
 *
 * @param ctrl 控制器指针
 * @param data 待写入数据
 * @return int 0 正确 <0 errno
 *
 */
int axispi_write(struct axispi_ctrl *ctrl, u8 data) {
    if (ctrl == NULL)
        return -ENODEV;

    int ret = axispi_wait(ctrl, ctrl->timout_ms, axispi_cmd_available);
    if (ret != 0)
        return ret;
    u32 cmd = data | AXI_SPI_CMD_WRITE;
    ctrl->reg->data = cmd;
    return 0;
}

/**
 * @brief 请求读取单字节数据
 *
 * @param ctrl 控制器指针
 * @param data 待写入数据
 * @return int 0 正确 <0 errno
 *
 */
int axispi_read_request(struct axispi_ctrl *ctrl) {
    if (ctrl == NULL)
        return -ENODEV;

    int ret = axispi_wait(ctrl, ctrl->timout_ms, axispi_cmd_available);
    if (ret != 0)
        return ret;
    u32 cmd = AXI_SPI_CMD_RW;
    ctrl->reg->data = cmd;
    return 0;
}

/**
 * @brief 请求读取单字节数据，并写入单字节数据
 *
 * @param ctrl 控制器指针
 * @param data 待写入数据
 * @return int 0 正确 <0 errno
 *
 */
int axispi_read_write_request(struct axispi_ctrl *ctrl, u8 data) {
    if (ctrl == NULL)
        return -ENODEV;

    int ret = axispi_wait(ctrl, ctrl->timout_ms, axispi_cmd_available);
    if (ret != 0)
        return ret;
    u32 cmd = data | AXI_SPI_CMD_RW;
    ctrl->reg->data = cmd;
    return 0;
}

/**
 * @brief 读取单字节数据
 *
 * @param ctrl 控制器指针
 * @return u8 读取的数据
 * @return int >=0 读取的数据 <0 errno
 */
int axispi_read(struct axispi_ctrl *ctrl) {
    if (ctrl == NULL)
        return -ENODEV;

    int ret = axispi_wait(ctrl, ctrl->timout_ms, axispi_rsp_available);
    if (ret != 0)
        return ret;
    u32 rsp = ctrl->reg->data;
    return rsp & 0xff;
}

/**
 * @brief 选择从设备
 *
 * @param ctrl 控制器指针
 * @param index 设备序号
 * @return int 0 正确 <0 errno
 */
int axispi_select(struct axispi_ctrl *ctrl, u8 index) {
    if (ctrl == NULL)
        return -ENODEV;

    if (index > AXI_SPI_MAX_DEVICES)
        return -EINVAL;

    int ret = axispi_wait(ctrl, ctrl->timout_ms, axispi_cmd_available);
    if (ret != 0)
        return ret;
    u32 cmd = index | AXI_SPI_CMD_ENABLE;
    ctrl->reg->data = cmd;
    return 0;
}

/**
 * @brief 取消选择从设备
 *
 * @param ctrl 控制器指针
 * @param index 设备序号
 * @return int 0 正确 <0 errno
 */
int axispi_diselect(struct axispi_ctrl *ctrl, u8 index) {
    if (ctrl == NULL)
        return -ENODEV;

    if (index > AXI_SPI_MAX_DEVICES)
        return -EINVAL;

    int ret = axispi_wait(ctrl, ctrl->timout_ms, axispi_cmd_available);
    if (ret != 0)
        return ret;
    u32 cmd = index | AXI_SPI_CMD_DISABLE;
    ctrl->reg->data = cmd;
    return 0;
}

/**
 * @brief 开启spi传输
 * 仅在continuous模式下有效
 *
 * @param ctrl 控制器指针
 * @param index 设备序号
 * @return int 0 正确 <0 errno
 */
int axispi_on(struct axispi_ctrl *ctrl) {
    if (!ctrl->continuous)
        return -EINVAL;

    ctrl->reg->cmd_pop_cond = 1;
    return 0;
}

/**
 * @brief 停止spi传输
 * 仅在continuous模式下有效
 *
 * @param ctrl 控制器指针
 * @param index 设备序号
 * @return int 0 正确 <0 errno
 */
int axispi_off(struct axispi_ctrl *ctrl) {
    if (!ctrl->continuous)
        return -EINVAL;

    int ret = axispi_wait(ctrl, ctrl->timout_ms, axispi_cancel_continuous_available);
    if (ret != 0)
        return ret;

    ctrl->reg->cmd_pop_cond = 0;
    return 0;
}
