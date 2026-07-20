package com.youzhi.zhixun.service;

import com.youzhi.zhixun.vo.DemoChatResponseVO;
import com.youzhi.zhixun.vo.WorkspaceVO;

public interface DemoExperienceService {
    WorkspaceVO workspace(String userId);

    DemoChatResponseVO answer(String question, String spaceId, String userId);
}
