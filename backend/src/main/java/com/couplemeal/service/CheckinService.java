package com.couplemeal.service;

import com.couplemeal.controller.CheckinController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 打卡服务（饭桌相册）：两步上传、提交打卡、当天修改、带饭方回应、相册分页。
 *
 * <p>见 docs/design/60-打卡/后端.md：
 *   - 上传只出两档图 + 相对路径，不写库不送审（§3.1）
 *   - 提交做权限/状态/窗口/图片数校验，失败码见 §3.2（3001/3002/3003/3004/3010）
 *   - 打卡不额外加爱心值，只发一条「已打卡」通知（§3.2）
 *   - 相册只返回已过审图片（§3.4）
 */
public interface CheckinService {

    /**
     * 上传打卡照片：类型白名单 → 重编码两档（剥 EXIF）→ 落盘，返回相对路径。
     *
     * @return 原图 + 缩略图相对路径
     */
    CheckinController.UploadResp upload(MultipartFile file);

    /**
     * 提交打卡。
     *
     * @return 新增打卡 id
     */
    Long submit(CheckinController.SubmitReq req);

    /**
     * 修改评价与点评：仅限打卡人本人在打卡当天，不提供删除。
     */
    void update(CheckinController.UpdateReq req);

    /**
     * 带饭方回应：≤50 字，覆盖式更新。
     */
    void reply(CheckinController.ReplyReq req);

    /**
     * 饭桌相册：按 create_time 倒序游标分页，按月分组。
     *
     * @param cursor 上一页最后一条的 createTime（yyyy-MM-dd HH:mm:ss），首页传空
     * @param size   页大小，缺省 10、上限 30
     */
    CheckinController.GalleryResp gallery(String cursor, Integer size);
}
