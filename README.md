# 车载电台 Android App

基于 Android 10+ 开发的车载收音机应用，支持网络电台播放、收藏管理、播放计划等功能。

## 功能特性

### 核心功能
- ✅ **启动页加载**：启动时自动加载省份列表和国家台电台列表
- ✅ **电台列表**：展示所有电台，包括图标、台名、正在播放的节目
- ✅ **上次播放**：自动恢复到上一次播放的电台
- ✅ **播放器界面**：全屏播放，显示电台信息和当前节目
- ✅ **后台播放**：支持应用退到后台继续播放
- ✅ **方向盘控制**：支持方向盘方向键换台、确认播放

### 扩展功能
- ✅ **收藏功能**：收藏喜欢的电台，查看收藏列表
- ✅ **播放计划**：设置定时自动切换电台播放
- ✅ **省份筛选**：按省份筛选电台列表

### 车载特性
- ✅ **强制横屏**：所有界面自动横屏，适配车载屏幕
- ✅ **深色模式**：专为车载环境设计的深色主题
- ✅ **大按钮设计**：适合驾驶时操作
- ✅ **方向盘控制**：支持方向盘按键控制换台和播放
- ✅ **Android 车载设备支持**：声明为车载应用，适配车机系统

## 技术栈

- **语言**: Kotlin
- **最低 SDK**: Android 10 (API 29)
- **目标 SDK**: Android 14 (API 34)
- **架构**: MVVM + Repository Pattern
- **本地存储**: Room Database
- **网络**: Retrofit2 + OkHttp
- **图片加载**: Coil
- **媒体播放**: ExoPlayer/Media3
- **后台任务**: WorkManager
- **UI 组件**: Material Design Components

## 项目结构

```
app/
├── src/main/java/com/carradio/
│   ├── CarRadioApplication.kt      # Application 类
│   ├── data/
│   │   ├── api/                    # API 接口
│   │   │   └── RadioApiService.kt
│   │   ├── db/                     # Room Database
│   │   │   ├── RadioDatabase.kt
│   │   │   ├── Converters.kt
│   │   │   ├── StationDao.kt
│   │   │   ├── ProvinceDao.kt
│   │   │   └── ScheduleDao.kt
│   │   ├── model/                  # 数据模型
│   │   │   ├── Station.kt
│   │   │   ├── Province.kt
│   │   │   └── Schedule.kt
│   │   └── repository/             # 数据仓库
│   │       └── RadioRepository.kt
│   ├── service/                    # 后台服务
│   │   └── RadioService.kt
│   ├── ui/                        # 界面层
│   │   ├── splash/
│   │   │   └── SplashActivity.kt
│   │   ├── main/
│   │   │   ├── MainActivity.kt
│   │   │   ├── MainViewModel.kt
│   │   │   └── StationAdapter.kt
│   │   ├── player/
│   │   │   └── PlayerActivity.kt
│   │   ├── favorites/
│   │   │   ├── FavoritesActivity.kt
│   │   │   └── FavoritesViewModel.kt
│   │   └── schedule/
│   │       ├── ScheduleActivity.kt
│   │       ├── ScheduleViewModel.kt
│   │       ├── ScheduleAdapter.kt
│   │       └── ScheduleDialogFragment.kt
│   └── worker/                    # 后台任务
│       └── ScheduleWorker.kt
├── res/                          # 资源文件
│   ├── layout/                   # 布局文件
│   ├── drawable/                 # 图标和图形
│   ├── values/                   # 字符串、颜色、样式
│   ├── anim/                     # 动画
│   ├── menu/                     # 菜单
│   ├── xml/                      # 配置文件
│   └── mipmap-*/                 # 启动图标
└── AndroidManifest.xml           # 应用清单
```

## API 接口

应用使用 `https://ytmsout.radio.cn` 作为 API 端点：

- **获取电台列表**: `/web/appBroadcast/list?categoryId={categoryId}&provinceCode={provinceCode}`
- **获取省份列表**: `/web/appProvince/list/all`

参数说明：
- `categoryId`: "0" 表示所有分类
- `provinceCode`: 省份代码，"0" 表示所有省份

## 数据库表

### Station (电台表)
| 字段 | 类型 | 说明 |
|------|------|------|
| contentId | String | 电台ID (主键) |
| title | String | 电台名称 |
| subtitle | String? | 当前节目 |
| imageUrl | String? | 图标URL |
| playUrlLow | String? | 低质量播放地址 |
| mp3PlayUrlLow | String? | 低质量MP3地址 |
| mp3PlayUrlHigh | String? | 高质量MP3地址 |
| playUrlMulti | String? | 多码率播放地址 |
| isFavorite | Boolean | 是否收藏 |
| lastPlayed | Long | 最后播放时间戳 |
| playCount | Int | 播放次数 |

### Province (省份表)
| 字段 | 类型 | 说明 |
|------|------|------|
| code | Int | 省份代码 (主键) |
| name | String | 省份名称 |

### Schedule (播放计划表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 自增主键 |
| stationId | String | 电台ID |
| stationName | String | 电台名称 |
| time | String | 时间 (HH:mm) |
| isEnabled | Boolean | 是否启用 |
| createdAt | Long | 创建时间 |
| updatedAt | Long | 更新时间 |

## 使用说明

### 开发环境
- Android Studio Hedgehog (2023.1.1) 或更高版本
- Kotlin 1.9+
- Gradle 8+

### 构建项目
```bash
# 调试版本
./gradlew assembleDebug

# 发布版本
./gradlew assembleRelease
```

### 在车载设备上测试
1. 确保设备运行 Android 10+ 并支持车载模式
2. 安装 APK 到设备
3. 应用会自动识别为车载应用并横屏显示

## 功能说明

### 播放控制
- **方向盘按键**：
  - 上下键：切换上一个/下一个电台
  - 确认键：播放当前选中电台
  - 播放/暂停键：控制播放状态

### 后台播放
- 点击播放器的"退出应用"按钮可将应用退到后台继续播放
- 通知栏显示播放状态和停止按钮
- 重启应用会恢复上一次播放的电台

### 播放计划
- 在计划页面可添加定时播放计划
- 设置时间和电台，系统会在指定时间自动切换播放
- 可随时启用/禁用计划
- WorkManager 每分钟检查一次是否有需要触发的计划

## 注意事项

1. **网络连接**：需要网络连接以获取电台列表和播放流
2. **驾驶安全**：建议在车辆停稳时进行复杂操作
3. **数据消耗**：播放电台会消耗流量，建议使用 WiFi 预加载
4. **电池优化**：为保持后台播放，可能需要关闭电池优化

## 待优化项

- [ ] 添加电台搜索功能
- [ ] 支持按分类筛选电台
- [ ] 支持多语言
- [ ] 添加播放历史记录
- [ ] 支持 Android Auto
- [ ] 添加音效均衡器
- [ ] 支持蓝牙/FM 切换
- [ ] 添加仪表盘小组件

## 许可证

MIT License

## 作者

Claude Code 生成