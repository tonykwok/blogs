#!/bin/bash
function print() {
    echo -e "\033[32;49;1mI: $1\033[39;49;0m"
}

function error() {
    echo -e "\033[32;31;1mE: $1\033[39;49;0m"
}

function usage() {
    echo -e "\033[32;49;1mUsage: apk-push x.apk 1 or apk-push x.apk 2\033[39;49;0m"
    echo -e "\033[32;49;1m    1: apk will be installed to /system/app/\033[39;49;0m"
    echo -e "\033[32;49;1m    2: apk will be installed to /system/priv-app/\033[39;49;0m"
    echo ""
}

function pause() {
    OLDCONFIG=`stty -g`
    stty -icanon -echo min 1 time 0
    dd count=1 2>/dev/null
    stty $OLDCONFIG
}

function waitKeyInput() {
   read -n1 -r ans
   case "$ans" in
        [Cc]|[Cc])
            # continue
            echo ""
        ;;

        *)
            exit 0
        ;;
   esac
}

if [ $# -lt 1 ]; then
    usage
    exit 1
fi

if [ $# -gt 2 ]; then
    usage
    exit 1
fi


if [ ! -f $1 ]; then
    error "$1 does not exist!"
    exit 1
fi

APK_FILE_PATH=$1
APK_PKG_NAME=`aapt dump badging $APK_FILE_PATH | grep package | awk '{print $2}' | sed s/name=//g | sed s/\'//g`
APK_FILE_NAME=$(echo $APK_FILE_PATH | sed "s/.*\///")
APK_FOLDER_NAME=$APK_PKG_NAME #$(echo $APK_FILE_PATH | sed "s/.*\///" | sed "s/\..*//")
NATIVE_LIB_COUNT=`unzip -l $APK_FILE_PATH | grep -c "armeabi"`
APK_PATH[0]="/system/app/$APK_FOLDER_NAME"
APK_PATH[1]="/system/priv-app/$APK_FOLDER_NAME"
APK_PATH[2]="/data/app/$APK_FOLDER_NAME"


# wait for adb connection
adb wait-for-device
sleep 2
adb root
sleep 2
adb remount

# disable verity
adb disable-verity

KEYWORDS="#UNDEFINED#"
if [ $# != 2 ]; then
    # sed 's!.*/\(.*\)/.*!\1!'
    # awk -F"/" '{print $3}'
    LOCATION=`adb shell pm list packages -f $APK_PKG_NAME | tr -d "\r" | grep "$APK_PKG_NAME$" | sed s/package://g | sed 's!\(.*\)/.*!\1!'`
    print "Found an existing old apk in target device: $LOCATION"
    TYPE=`echo $LOCATION | sed 's!\(.*\)/.*!\1!'`
    case "$TYPE" in  
    "/data/app")
        APK_FILE_TYPE=3
        print "$APK_FILE_NAME is not a system app, please use 'adb install -r $APK_FILE_PATH' instead!"
        ;;
    "/system/priv-app")
        APK_FILE_TYPE=2
        ;;
    "/system/app")
        APK_FILE_TYPE=1
        ;;
    *)
        usage
        exit 1
        ;;
    esac

    KEYWORDS=`adb shell pm list packages -f $APK_PKG_NAME | tr -d "\r" | grep "$APK_PKG_NAME$" | sed s/package://g | cut -d "/" -f4`
else
    APK_FILE_TYPE=$2
fi

if expr "$APK_FILE_TYPE" : '[0-9]*' > /dev/null ; then
    if [ $APK_FILE_TYPE -gt 2 ] ; then
        usage
        exit 1
    fi

    if [ $APK_FILE_TYPE -lt 1 ] ; then
        usage
        exit 1
    fi
else
    usage
    exit 1
fi


index=`expr $APK_FILE_TYPE - 1`
TARGET_INSTALL_FOLDER=${APK_PATH[index]}


# waiting for user's confirmation
print "The old apk will be uninstalled or removed before pushing the new one to $TARGET_INSTALL_FOLDER, continue [C] or abort [A]?"
waitKeyInput

if [ $KEYWORDS != "#UNDEFINED#" ]; then
    print "Unistall the old apk, package: $APK_PKG_NAME, keyword: $KEYWORDS"
    adb uninstall $APK_PKG_NAME

    # remove old apk from target device's /system/priv-app/ folder
    while read -r line; do
        print "Remove /system/priv-app/$line"
        adb shell rm -rf /system/priv-app/$line
        if [ $? -gt 0 ]; then
            error "Failed to remove /system/priv-app/$line"
            exit 1
        fi 
    done < <(adb shell ls /system/priv-app/ | grep $KEYWORDS | tr -d "\r")

    # remove old apk from target device's /system/app/ folder
    while read -r line; do
        print "Remove /system/app/$line"
        adb shell rm -rf /system/app/$line
        if [ $? -gt 0 ]; then
            error "Failed to remove /system/app/$line"
            exit 1
        fi 
    done < <(adb shell ls /system/app/ | grep $KEYWORDS | tr -d "\r")
    
    # remove old apk from target device's /data/app/ folder
    while read -r line; do
        print "Remove /data/app/$line"
        adb shell rm -rf /data/app/$line
        if [ $? -gt 0 ]; then
            error "Failed to remove /data/app/$line"
            exit 1
        fi 
    done < <(adb shell ls /data/app/ | grep $KEYWORDS | tr -d "\r")

    # remove cached dex from target device's /lib/arm folder
    while read -r line; do
        print "Remove /data/dalvik-cache/arm/$line"
        adb shell rm -rf /data/dalvik-cache/arm/$line
        if [ $? -gt 0 ]; then
            error "Failed to remove /data/dalvik-cache/arm/$line"
            exit 1
        fi 
    done < <(adb shell ls /data/dalvik-cache/arm/ | grep $KEYWORDS | tr -d "\r")

    # remove cached dex from target devices's lib/arm64 folder
    while read -r line; do
        print "Remove /data/dalvik-cache/arm64/$line"
        adb shell rm -rf /data/dalvik-cache/arm64/$line
        if [ $? -gt 0 ]; then
            error "Failed to remove remove /data/dalvik-cache/arm64/$line"
            exit 1
        fi 
    done < <(adb shell ls /data/dalvik-cache/arm64/ | grep $KEYWORDS | tr -d "\r")
else
    print "$APK_FILE_NAME is not found on target device, so uninstall or remove is ignored!"
fi    

# create apk folder in target device
print "Create $TARGET_INSTALL_FOLDER in target device"
adb shell mkdir -p $TARGET_INSTALL_FOLDER
if [ $? -gt 0 ]; then
    error "Failed to create $TARGET_INSTALL_FOLDER in target device"
    exit 1
fi

# create lib/arm folder in target device
if [ $NATIVE_LIB_COUNT -gt 0 ]; then
    print "Create $TARGET_INSTALL_FOLDER/lib/arm in target device"
    adb shell mkdir -p $TARGET_INSTALL_FOLDER/lib/arm
    if [ $? -gt 0 ]; then
        error "Failed to create $TARGET_INSTALL_FOLDER/lib/arm in target device"
        exit 1
    fi
fi

# check if sepecified apk contains *.so files or not
if [ $NATIVE_LIB_COUNT -gt 0 ]; then
    # remove alreay existing lib folder
    if [ -d lib ]; then
        rm -rf lib
    fi

    # extract *.so files from the apk
    unzip $APK_FILE_PATH lib/*
    if [ $? -gt 0 ]; then
        error "Failed to extract *.so files from $APK_FILE_NAME"
        exit 1
    fi

    if [ -d lib ]; then
        print "Copy *.so files to lib/arm"
        mkdir lib/arm
        if [ $? -gt 0 ]; then
            error "Failed to create lib/arm folder"
            exit 1
        fi

        if [ -d lib/armeabi ]; then
            mv lib/armeabi/*.so lib/arm
            if [ $? -gt 0 ]; then
                error "Failed to copy *.so files to lib/arm"
                exit 1
            fi
        fi

        if [ -d lib/armeabi-v7a ]; then
            mv lib/armeabi-v7a/*.so lib/arm
            if [ $? -gt 0 ]; then
                error "Failed to copy *.so files to lib/arm"
                exit 1
            fi
        fi
    fi
fi

# push apk
print "Push $APK_FILE_NAME -> $TARGET_INSTALL_FOLDER"
adb push $APK_FILE_PATH $TARGET_INSTALL_FOLDER
if [ $? -gt 0 ]; then
    error "Failed to push $APK_FILE_NAME to $TARGET_INSTALL_FOLDER"
    exit 1
fi

# push lib
if [ $NATIVE_LIB_COUNT -gt 0 ]; then
    print "Push lib -> $TARGET_INSTALL_FOLDER/lib"
    adb push lib $TARGET_INSTALL_FOLDER/lib
    if [ $? -gt 0 ]; then
        error "Failed to push lib -> $TARGET_INSTALL_FOLDER/lib"
        exit 1
    fi

    # remove unused lib folder
    if [ -d lib ]; then  
        rm -rf lib
    fi
fi

print "Reboot is required to make changes effective, continue [C] or abort [A]?"
waitKeyInput

adb reboot
print "Rebooting..."
