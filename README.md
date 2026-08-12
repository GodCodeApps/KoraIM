# ZCHD-Component

Android组件仓库项目

#### im - UI框架

#### imCore - im核心（websocket层,db层）

## 初始化启动服务

        IMClient.init(this)

## 设置当前用户id

     ImSdkImpl.setAccount("test1")

## 设置当前用户id

     ImSdkImpl.setAccount("test1")

## 新增消息类型

     MsgViewHolderFactory.register(ImageAttachment::class.java,MsgImageViewHolder::class.java)
     com.kora.imcore.attachment.MsgAttachment下添加新的Attachment

