/**
 * @file types.h
 * @author wlanxww (xueweiwujxw@outlook.com)
 * @brief
 * @date 2024-12-20
 *
 * @copyright Copyright (c) 2024
 *
 */

#pragma once
#include <stdint.h>

typedef uint64_t u64;
typedef int64_t i64;

typedef uint32_t u32;
typedef int32_t i32;

typedef uint16_t u16;
typedef int16_t i16;

typedef uint8_t u8;
typedef int8_t i8;

#ifndef BIT
#define BIT(x) (1UL << (x))
#endif

#ifndef GENMASK
#define GENMASK(h, l) (((~0UL) << (l)) & (~0UL >> (sizeof(long) * 8 - 1 - (h))))
#endif