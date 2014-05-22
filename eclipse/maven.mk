PWD=$(shell pwd)

ANDROID_OUT=$(realpath $(PWD)/../../out)
ECLIPSE_PREBUILTS=$(realpath $(PWD)/../../prebuilts/eclipse)
ECLIPSE_BUILD_DEPS=$(realpath $(PWD)/../../prebuilts/eclipse-build-deps)

TARGET_DIR=$(ANDROID_OUT)/host/maven/target

all: setup build

setup:
	mkdir -p $(TARGET_DIR)
	unzip -u $(ECLIPSE_BUILD_DEPS)/platform/org.eclipse.platform-4.2.2.zip -d $(TARGET_DIR)/platform
	unzip -u $(ECLIPSE_BUILD_DEPS)/cdt/cdt-master-8.0.2.zip -d $(TARGET_DIR)/cdt
	unzip -u $(ECLIPSE_BUILD_DEPS)/emf/emf-xsd-Update-2.9.1.zip -d $(TARGET_DIR)/emf
	unzip -u $(ECLIPSE_BUILD_DEPS)/jdt/org.eclipse.jdt.source-4.2.2.zip -d $(TARGET_DIR)/jdt
	unzip -u $(ECLIPSE_BUILD_DEPS)/wtp/wtp-repo-R-3.3.2-20120210195245.zip -d $(TARGET_DIR)/wtp
	unzip -u $(ECLIPSE_BUILD_DEPS)/gef/GEF-Update-3.9.1.zip -d $(TARGET_DIR)/gef
	unzip -u $(ECLIPSE_BUILD_DEPS)/pde/org.eclipse.pde-3.8.zip -d $(TARGET_DIR)/pde
	unzip -u $(ECLIPSE_BUILD_DEPS)/egit/org.eclipse.egit.repository-2.2.0.201212191850-r.zip -d $(TARGET_DIR)/egit

build:
	$(ECLIPSE_PREBUILTS)/maven/apache-maven-3.2.1/bin/mvn -s settings.xml -DforceContextQualifier=M01 -DANDROID_OUT=$(ANDROID_OUT) package
