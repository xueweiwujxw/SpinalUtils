CC ?= gcc
AR ?= ar

CFLAGS := -Wall -Wextra -O2 -I./src
LDFLAGS :=

SRCDIR := src

BUILDDIR := build

LIB := $(BUILDDIR)/libaxispi.a

SRCS := $(wildcard $(SRCDIR)/*.c)

OBJS := $(patsubst $(SRCDIR)/%.c, $(BUILDDIR)/%.o, $(SRCS))

all: $(LIB)

$(LIB): $(OBJS)
	@echo "Archiving: $@"
	@mkdir -p $(BUILDDIR)
	$(AR) rcs $@ $^

$(BUILDDIR)/%.o: $(SRCDIR)/%.c
	@mkdir -p $(BUILDDIR)
	@echo "Compiling: $< -> $@"
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	@echo "Cleaning build directory..."
	rm -rf $(BUILDDIR)

.PHONY: all clean
