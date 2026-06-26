## 聊天助手管理

------

### 创建聊天助手

**POST** `/api/v1/chats`

创建一个聊天助手。

#### 请求

- 方法: POST
- URL: `/api/v1/chats`
- 请求头
  - `'content-Type: application/json'`
  - `'Authorization: Bearer <YOUR_API_KEY>'`
- 请求体
  - `"name"`: `string`
  - `"icon"`: `string`
  - `"dataset_ids"`: `list[string]`
  - `"llm_id"`: `string`
  - `"llm_setting"`: `object`
  - `"prompt_config"`: `object`

##### 请求示例

```shell
curl --request POST \
     --url http://{address}/api/v1/chats \
     --header 'Content-Type: application/json' \
     --header 'Authorization: Bearer <YOUR_API_KEY>' \
     --data '{
    "dataset_ids": ["0b2cbc8c877f11ef89070242ac120005"],
    "name":"new_chat_1"
}'
```

##### 请求参数

- `"name"`: (*请求体参数*), `string`, *必填* 聊天助手的名称。

- `"icon"`: (*请求体参数*), `string` 头像的Base64编码。

- `"dataset_ids"`: (*请求体参数*), `list[string]` 关联数据集的唯一标识符。如果省略或设置为 `[]`，则创建一个空的聊天助手；数据集可在以后附加。

- `"llm_id"`: (*请求体参数*), `string` 聊天模型的标识符。如果未指定，系统将默认为用户预配置的聊天模型。

- ```
  "llm_setting"
  ```

  : (

  请求体参数

  ),

   

  ```
  object
  ```

   

  定义助手LLM参数的配置对象。

  ```
  llm_setting
  ```

   

  对象可能包含以下属性

  - `"model_type"`: `string` 模型类型说明符。只识别 `"chat"` 和 `"image2text"`；任何其他输入，或省略时，均视为 `"chat"`。
  - `"temperature"`: `float` 控制模型预测的随机性。较低的温度会导致更保守的响应，而较高的温度会产生更具创造性和多样性的响应。默认为 `0.1`。
  - `"top_p"`: `float` 也称为“核采样”，此参数设置一个阈值，用于从一小组词中进行采样。它侧重于最可能的词，排除可能性较低的词。默认为 `0.3`
  - `"presence_penalty"`: `float` 这通过惩罚对话中已出现的词来阻止模型重复相同的信息。默认为 `0.4`。
  - `"frequency penalty"`: `float` 与存在惩罚类似，这减少了模型频繁重复相同词语的倾向。默认为 `0.7`。

- ```
  "prompt_config"
  ```

  : (

  请求体参数

  ),

   

  ```
  object
  ```

   

  LLM应遵循的指令。

  ```
  prompt_config
  ```

   

  对象可能包含以下属性

  - `"system"`: `string` 提示内容。

  - `"prologue"`: `string` 给用户的开场白。

  - ```
    "parameters"
    ```

    :

     

    ```
    object[]
    ```

     

    此参数列出了要在系统提示中使用的变量。请注意，

    - `"knowledge"` 是一个保留变量，表示检索到的块。
    - `"system"` 中的所有变量都应使用花括号括起来。

  - `"empty_response"`: `string` 如果数据集中没有检索到与用户问题相关的内容，这将用作响应。要允许LLM在未找到内容时即兴发挥，请留空此字段。

  - `"quote"`: `boolean` 是否显示文本来源。默认为 `true`。

  - `"tts"`: `boolean`

  - `"refine_multiturn"`: `boolean`

  - `"use_kg"`: `boolean`

  - `"reasoning"`: `boolean`

  - `"cross_languages"`: `list[string]`

  - `"tavily_api_key"`: `string`

  - `"toc_enhance"`: `boolean`

- `"similarity_threshold"`: (*请求体参数*), `float`

- `"vector_similarity_weight"`: (*请求体参数*), `float`

- `"top_n"`: (*请求体参数*), `int`

- `"top_k"`: (*请求体参数*), `int`

- `"rerank_id"`: (*请求体参数*), `string`

#### 响应

成功

```json
{
    "code": 0,
    "data": {
        "icon": "",
        "create_date": "Thu, 24 Oct 2024 11:18:29 GMT",
        "create_time": 1729768709023,
        "dataset_ids": [
            "527fa74891e811ef9c650242ac120006"
        ],
        "kb_names": [
            "dataset_1"
        ],
        "description": "A helpful Assistant",
        "id": "b1f2f15691f911ef81180242ac120003",
        "language": "English",
        "llm_id": "qwen-plus@Tongyi-Qianwen",
        "llm_setting": {
            "frequency_penalty": 0.7,
            "presence_penalty": 0.4,
            "temperature": 0.1,
            "top_p": 0.3
        },
        "name": "12234",
        "prompt_config": {
            "empty_response": "Sorry! No relevant content was found in the knowledge base!",
            "prologue": "Hi! I'm your assistant. What can I do for you?",
            "quote": true,
            "system": "You are an intelligent assistant...",
            "parameters": [
                {
                    "key": "knowledge",
                    "optional": false
                }
            ]
        },
        "rerank_id": "",
        "similarity_threshold": 0.2,
        "vector_similarity_weight": 0.3,
        "top_n": 6,
        "prompt_type": "simple",
        "status": "1",
        "tenant_id": "69736c5e723611efb51b0242ac120007",
        "top_k": 1024,
        "update_date": "Thu, 24 Oct 2024 11:18:29 GMT",
        "update_time": 1729768709023
    }
}
```

失败

```json
{
    "code": 102,
    "message": "Duplicated chat name."
}
```

------

### 更新聊天助手

**PUT** `/api/v1/chats/{chat_id}`

覆盖指定聊天助手的现有配置。

仅在提供完整配置时使用此端点。请求中省略的任何字段都将重置为其服务器端默认值。对于部分更新，请改用 `PATCH /api/v1/chats/{chat_id}`。

#### 请求

- 方法: PUT
- URL: `/api/v1/chats/{chat_id}`
- 请求头
  - `'content-Type: application/json'`
  - `'Authorization: Bearer <YOUR_API_KEY>'`
- 请求体
  - `"name"`: `string`
  - `"icon"`: `string`
  - `"dataset_ids"`: `list[string]`
  - `"llm_id"`: `string`
  - `"llm_setting"`: `object`
  - `"prompt_config"`: `object`

##### 请求示例

```bash
curl --request PUT \
     --url http://{address}/api/v1/chats/{chat_id} \
     --header 'Content-Type: application/json' \
     --header 'Authorization: Bearer <YOUR_API_KEY>' \
     --data '
     {
          "name":"Test",
          "icon":"",
          "dataset_ids":["0b2cbc8c877f11ef89070242ac120005"],
          "llm_id":"qwen-plus@Tongyi-Qianwen",
          "llm_setting":{"temperature":0.1,"top_p":0.3,"presence_penalty":0.4,"frequency_penalty":0.7},
          "prompt_config":{
               "system":"You are an intelligent assistant...",
               "prologue":"Hi! I'\''m your assistant. What can I do for you?",
               "parameters":[{"key":"knowledge","optional":false}],
               "empty_response":"Sorry! No relevant content was found in the knowledge base!",
               "quote":true
          },
          "similarity_threshold":0.2,
          "vector_similarity_weight":0.3,
          "top_n":6,
          "top_k":1024,
          "rerank_id":""
     }'
```

#### 参数

- `chat_id`: (*路径参数*) 要更新的聊天助手ID。

- `"name"`: (*Body parameter*), `string`, *Required* 聊天助手的修改名称。

- `"icon"`: (*请求体参数*), `string` 头像的Base64编码。

- `"dataset_ids"`: (*Body parameter*), `list[string]` 关联数据集的ID。

- `"llm_id"`: (*Body parameter*), `string` 聊天模型名称。如果未设置，则使用用户的默认聊天模型。

- ```
  "llm_setting"
  ```

  : (

  Body parameter

  ),

   

  ```
  object
  ```

   

  聊天助手的LLM设置。一个

   

  ```
  llm_setting
  ```

   

  对象包含以下属性

  - `"model_type"`: `string` 模型类型指定符。支持的值为 `"chat"` 和 `"image2text"`。如果该字段被省略或提供的值无法识别，则默认为 `"chat"`。
  - `"temperature"`: `float` 控制模型预测的随机性。较低的温度会导致更保守的响应，而较高的温度会产生更具创造性和多样性的响应。默认为 `0.1`。
  - `"top_p"`: `float` 也称为“核采样”，此参数设置一个阈值，用于从一小组词中进行采样。它侧重于最可能的词，排除可能性较低的词。默认为 `0.3`
  - `"presence_penalty"`: `float` 这通过惩罚对话中已出现的词来阻止模型重复相同的信息。默认为 `0.4`。
  - `"frequency penalty"`: `float` 与存在惩罚类似，这减少了模型频繁重复相同词语的倾向。默认为 `0.7`。

- `"prompt_config"`: (*Body parameter*), `object`

- `"similarity_threshold"`: (*请求体参数*), `float`

- `"vector_similarity_weight"`: (*请求体参数*), `float`

- `"top_n"`: (*请求体参数*), `int`

- `"top_k"`: (*请求体参数*), `int`

- `"rerank_id"`: (*请求体参数*), `string`

对于 `PUT` 请求，请求体中省略的任何字段都将重置为服务器端默认值。

#### 响应

成功：返回完整的已更新聊天助手对象。

```json
{
    "code": 0,
    "data": {
        "id": "04d0d8e28d1911efa3630242ac120006",
        "name": "Test",
        "description": "A helpful Assistant",
        "icon": "",
        "dataset_ids": ["527fa74891e811ef9c650242ac120006"],
        "kb_names": ["dataset_1"],
        "llm_id": "qwen-plus@Tongyi-Qianwen",
        "llm_setting": {
            "frequency_penalty": 0.7,
            "presence_penalty": 0.4,
            "temperature": 0.1,
            "top_p": 0.3
        },
        "prompt_config": {
            "empty_response": "Sorry! No relevant content was found in the knowledge base!",
            "prologue": "Hi! I'm your assistant. What can I do for you?",
            "quote": true,
            "system": "You are an intelligent assistant...",
            "parameters": [{"key": "knowledge", "optional": false}]
        },
        "similarity_threshold": 0.2,
        "vector_similarity_weight": 0.3,
        "top_n": 6,
        "top_k": 1024,
        "rerank_id": "",
        "status": "1",
        "tenant_id": "69736c5e723611efb51b0242ac120007",
        "create_time": 1729232406637,
        "update_time": 1729232406638
    }
}
```

失败

```json
{
    "code": 102,
    "message": "Duplicated chat name."
}
```

------

### 获取聊天助手

**GET** `/api/v1/chats/{chat_id}`

检索指定的聊天助手。

#### 请求

- 方法: GET
- URL: `/api/v1/chats/{chat_id}`
- 请求头
  - `'Authorization: Bearer <YOUR_API_KEY>'`

##### 请求示例

```bash
curl --request GET \
     --url http://{address}/api/v1/chats/{chat_id} \
     --header 'Authorization: Bearer <YOUR_API_KEY>'
```

##### 请求参数

- `chat_id`: (*Path parameter*) 要检索的聊天助手的ID。

#### 响应

成功

```json
{
    "code": 0,
    "data": {
        "icon": "",
        "create_date": "Fri, 18 Oct 2024 06:20:06 GMT",
        "create_time": 1729232406637,
        "description": "A helpful Assistant",
        "id": "04d0d8e28d1911efa3630242ac120006",
        "dataset_ids": ["527fa74891e811ef9c650242ac120006"],
        "kb_names": ["dataset_1"],
        "language": "English",
        "llm_id": "qwen-plus@Tongyi-Qianwen",
        "llm_setting": {
            "temperature": 0.1,
            "top_p": 0.3
        },
        "name": "my_chat",
        "prompt_config": {
            "empty_response": "Sorry! No relevant content was found in the knowledge base!",
            "prologue": "Hi! I'm your assistant. What can I do for you?",
            "quote": true,
            "system": "You are an intelligent assistant...",
            "parameters": [{"key": "knowledge", "optional": false}]
        },
        "rerank_id": "",
        "similarity_threshold": 0.2,
        "vector_similarity_weight": 0.3,
        "top_n": 6,
        "status": "1",
        "tenant_id": "69736c5e723611efb51b0242ac120007",
        "update_date": "Fri, 18 Oct 2024 06:20:06 GMT",
        "update_time": 1729232406638
    }
}
```

失败

```json
{
    "code": 102,
    "message": "No authorization."
}
```

------

### 部分更新聊天助手

**PATCH** `/api/v1/chats/{chat_id}`

对指定的聊天助手执行部分更新。

未指定的字段将被保留，而嵌套对象（例如 `llm_setting` 和 `prompt_config`）将与现有配置进行深度合并。这是重命名助手或修改特定设置子集的推荐端点。

#### 请求

- 方法：PATCH
- URL: `/api/v1/chats/{chat_id}`
- 请求头
  - `'content-Type: application/json'`
  - `'Authorization: Bearer <YOUR_API_KEY>'`
- 请求体：`PUT /api/v1/chats/{chat_id}` 接受的字段的任意子集

##### 请求示例

```bash
curl --request PATCH \
     --url http://{address}/api/v1/chats/{chat_id} \
     --header 'Content-Type: application/json' \
     --header 'Authorization: Bearer <YOUR_API_KEY>' \
     --data '{
    "llm_id": "gpt-4o",
    "llm_setting": {"temperature": 0.5}
}'
```

#### 响应

成功：返回完整的已更新聊天助手对象（与 `PUT /api/v1/chats/{chat_id}` 的结构相同）。

```json
{
    "code": 0,
    "data": {
        "id": "04d0d8e28d1911efa3630242ac120006",
        "name": "Renamed assistant",
        "llm_id": "qwen-plus@Tongyi-Qianwen",
        "..."  : "..."
    }
}
```

失败

```json
{
    "code": 102,
    "message": "No authorization."
}
```

------

### 删除聊天助手

**DELETE** `/api/v1/chats/{chat_id}`

按ID删除聊天助手。

#### 请求

- 方法: DELETE
- URL: `/api/v1/chats/{chat_id}`
- 请求头
  - `'Authorization: Bearer <YOUR_API_KEY>'`

##### 请求示例

```bash
curl --request DELETE \
     --url http://{address}/api/v1/chats/{chat_id} \
     --header 'Authorization: Bearer <YOUR_API_KEY>'
```

##### 请求参数

- `chat_id`: (*Path parameter*) 要删除的聊天助手的ID。

#### 响应

成功

```json
{
    "code": 0,
    "data": true
}
```

失败

```json
{
    "code": 102,
    "message": "No authorization."
}
```

------

### 删除聊天助手

**DELETE** `/api/v1/chats`

按ID删除聊天助手。

已弃用

请求体中的 `chat_id` 已废弃，请使用 `ids` 列表。

#### 请求

- 方法: DELETE
- URL: `/api/v1/chats`
- 请求头
  - `'content-Type: application/json'`
  - `'Authorization: Bearer <YOUR_API_KEY>'`
- 请求体
  - `"ids"`: `list[string]`
  - `"delete_all"`: `boolean`

##### 请求示例

```bash
curl --request DELETE \
     --url http://{address}/api/v1/chats \
     --header 'Content-Type: application/json' \
     --header 'Authorization: Bearer <YOUR_API_KEY>' \
     --data '
     {
          "ids": ["test_1", "test_2"]
     }'
curl --request DELETE \
     --url http://{address}/api/v1/chats \
     --header 'Content-Type: application/json' \
     --header 'Authorization: Bearer <YOUR_API_KEY>' \
     --data '{
          "delete_all": true
     }'
```

##### 请求参数

- ```
  "ids"
  ```

  : (

  Body parameter

  ),

   

  ```
  list[string]
  ```

   

  要删除的聊天助手的ID。

  - 如果省略，或设置为 `null` 或空数组，则不删除任何聊天助手。
  - 如果提供了ID数组，则只删除与这些ID匹配的聊天助手。

- `"delete_all"`: (*Body parameter*), `boolean` 当 `"ids"` 被省略，或设置为 `null` 或空数组时，是否删除当前用户拥有的所有聊天助手。默认为 `false`。

#### 响应

成功

```json
{
    "code": 0
}
```

失败

```json
{
    "code": 102,
    "message": "ids are required"
}
```

------

### 列出聊天助手

**GET** `/api/v1/chats?page={page}&page_size={page_size}&orderby={orderby}&desc={desc}&keywords={keywords}&owner_ids={owner_id}&name={chat_name}&id={chat_id}`

列出聊天助手。

#### 请求

- 方法: GET
- URL: `/api/v1/chats?page={page}&page_size={page_size}&orderby={orderby}&desc={desc}&keywords={keywords}&owner_ids={owner_id}&name={chat_name}&id={chat_id}`
- 请求头
  - `'Authorization: Bearer <YOUR_API_KEY>'`

##### 请求示例

```bash
curl --request GET \
     --url http://{address}/api/v1/chats?page={page}&page_size={page_size}&orderby={orderby}&desc={desc}&keywords={keywords}&owner_ids={owner_id}&name={chat_name}&id={chat_id} \
     --header 'Authorization: Bearer <YOUR_API_KEY>'
```

##### 请求参数

- `page`: (*Filter parameter*), `integer` 指定将显示聊天助手的页码。默认为 `1`。

- `page_size`: (*Filter parameter*), `integer` 每页显示的聊天助手数量。默认为 `30`。

- ```
  orderby
  ```

  : (

  Filter parameter

  ),

   

  ```
  string
  ```

   

  结果排序所依据的属性。可用选项

  - `create_time`（默认）
  - `update_time`

- `desc`: (*Filter parameter*), `boolean` 指示检索到的聊天助手是否应按降序排序。默认为 `true`。

- `keywords`: (*Filter parameter*), `string` 对聊天助手名称进行不区分大小写的模糊匹配。

- `owner_ids`: (*Filter parameter*), `string` （可重复）按所有者租户ID过滤。可多次指定：`?owner_ids=id1&owner_ids=id2`。

- `id`: (*Filter parameter*), `string` 要精确匹配检索的聊天助手的ID。

- `name`: (*Filter parameter*), `string` 要精确匹配检索的聊天助手的名称。

当提供了 `id` 或 `name` 时，精确过滤优先于 `keywords`。

#### 响应

成功

```json
{
    "code": 0,
    "data": {
        "chats": [
            {
                "icon": "",
                "create_date": "Fri, 18 Oct 2024 06:20:06 GMT",
                "create_time": 1729232406637,
                "description": "A helpful Assistant",
                "id": "04d0d8e28d1911efa3630242ac120006",
                "dataset_ids": ["527fa74891e811ef9c650242ac120006"],
                "kb_names": ["dataset_1"],
                "language": "English",
                "llm_id": "qwen-plus@Tongyi-Qianwen",
                "llm_setting": {
                    "frequency_penalty": 0.7,
                    "presence_penalty": 0.4,
                    "temperature": 0.1,
                    "top_p": 0.3
                },
                "name": "13243",
                "prompt_config": {
                    "empty_response": "Sorry! No relevant content was found in the knowledge base!",
                    "prologue": "Hi! I'm your assistant. What can I do for you?",
                    "quote": true,
                    "system": "You are an intelligent assistant...",
                    "parameters": [
                        {
                            "key": "knowledge",
                            "optional": false
                        }
                    ]
                },
                "rerank_id": "",
                "similarity_threshold": 0.2,
                "vector_similarity_weight": 0.3,
                "top_n": 6,
                "prompt_type": "simple",
                "status": "1",
                "tenant_id": "69736c5e723611efb51b0242ac120007",
                "update_date": "Fri, 18 Oct 2024 06:20:06 GMT",
                "update_time": 1729232406638
            }
        ],
        "total": 1
    }
}
```

失败

```json
{
    "code": 102,
    "message": "The chat doesn't exist"
}
```

------

## 会话管理

------

### 创建与聊天助手的会话

**POST** `/api/v1/chats/{chat_id}/sessions`

创建一个与聊天助手的会话。

#### 请求

- 方法: POST
- URL: `/api/v1/chats/{chat_id}/sessions`
- 请求头
  - `'content-Type: application/json'`
  - `'Authorization: Bearer <YOUR_API_KEY>'`
- 请求体
  - `"name"`: `string`
  - `"user_id"`: `string` （可选）

##### 请求示例

```bash
curl --request POST \
     --url http://{address}/api/v1/chats/{chat_id}/sessions \
     --header 'Content-Type: application/json' \
     --header 'Authorization: Bearer <YOUR_API_KEY>' \
     --data '
     {
          "name": "new session"
     }'
```

##### 请求参数

- `chat_id`: (*Path parameter*) 关联聊天助手的ID。
- `"name"`: (*Body parameter*), `string` 要创建的聊天会话的名称。
- `"user_id"`: (*Body parameter*), `string` 可选的用户自定义ID。

#### 响应

成功

```json
{
    "code": 0,
    "data": {
        "chat_id": "2ca4b22e878011ef88fe0242ac120005",
        "create_date": "Fri, 11 Oct 2024 08:46:14 GMT",
        "create_time": 1728636374571,
        "id": "4606b4ec87ad11efbc4f0242ac120006",
        "messages": [
            {
                "content": "Hi! I am your assistant, can I help you?",
                "role": "assistant"
            }
        ],
        "name": "new session",
        "update_date": "Fri, 11 Oct 2024 08:46:14 GMT",
        "update_time": 1728636374571
    }
}
```

失败

```json
{
    "code": 102,
    "message": "`name` can not be empty."
}
```

------

### 更新聊天助手的会话

**PATCH** `/api/v1/chats/{chat_id}/sessions/{session_id}`

更新指定聊天助手的会话。

已弃用

之前的端点 `PUT /api/v1/chats/{chat_id}/sessions/{session_id}` 已废弃。请使用此端点。

#### 请求

- 方法：PATCH
- URL: `/api/v1/chats/{chat_id}/sessions/{session_id}`
- 请求头
  - `'content-Type: application/json'`
  - `'Authorization: Bearer <YOUR_API_KEY>'`
- 请求体
  - `"name"`: `string`

##### 请求示例

```bash
curl --request PATCH \
     --url http://{address}/api/v1/chats/{chat_id}/sessions/{session_id} \
     --header 'Content-Type: application/json' \
     --header 'Authorization: Bearer <YOUR_API_KEY>' \
     --data '
     {
          "name": "<REVISED_SESSION_NAME_HERE>"
     }'
```

##### 请求参数

- `chat_id`: (*Path parameter*) 关联聊天助手的ID。
- `session_id`: (*Path parameter*) 要更新的会话的ID。
- `"name"`: (*Body Parameter*), `string` 会话的修改名称。

#### 响应

成功

```json
{
    "code": 0,
    "data": {
        "chat_id": "2ca4b22e878011ef88fe0242ac120005",
        "create_date": "Fri, 11 Oct 2024 08:46:14 GMT",
        "create_time": 1728636374571,
        "id": "4606b4ec87ad11efbc4f0242ac120006",
        "messages": [
            {
                "content": "Hi! I am your assistant, can I help you?",
                "role": "assistant"
            }
        ],
        "name": "updated session name",
        "update_date": "Fri, 11 Oct 2024 08:46:14 GMT",
        "update_time": 1728636374571,
        "user_id": ""
    }
}
```

失败

```json
{
    "code": 102,
    "message": "`name` can not be empty."
}
```

------

### 列出聊天助手的会话

**GET** `/api/v1/chats/{chat_id}/sessions?page={page}&page_size={page_size}&orderby={orderby}&desc={desc}&name={session_name}&id={session_id}&user_id={user_id}`

列出与指定聊天助手关联的会话。

#### 请求

- 方法: GET
- URL: `/api/v1/chats/{chat_id}/sessions?page={page}&page_size={page_size}&orderby={orderby}&desc={desc}&name={session_name}&id={session_id}&user_id={user_id}`
- 请求头
  - `'Authorization: Bearer <YOUR_API_KEY>'`

##### 请求示例

```bash
curl --request GET \
     --url http://{address}/api/v1/chats/{chat_id}/sessions?page={page}&page_size={page_size}&orderby={orderby}&desc={desc}&name={session_name}&id={session_id}&user_id={user_id} \
     --header 'Authorization: Bearer <YOUR_API_KEY>'
```

##### 请求参数

- `chat_id`: (*Path parameter*) 关联聊天助手的ID。

- `page`: (*Filter parameter*), `integer` 指定将显示会话的页码。默认为 `1`。

- `page_size`: (*Filter parameter*), `integer` 每页显示的会话数量。默认为 `30`。如果设置为 `0`，则返回空列表。

- ```
  orderby
  ```

  : (

  Filter parameter

  ),

   

  ```
  string
  ```

   

  会话排序所依据的字段。可用选项

  - `create_time`（默认）
  - `update_time`

- `desc`: (*Filter parameter*), `boolean` 指示检索到的会话是否应按降序排序。默认为 `true`。

- `name`: (*Filter parameter*) `string` 要检索的聊天会话的名称。

- `id`: (*Filter parameter*), `string` 要检索的聊天会话的ID。

- `user_id`: (*Filter parameter*), `string` 创建会话时传入的可选用户自定义ID。

#### 响应

成功

```json
{
    "code": 0,
    "data": [
        {
            "chat_id": "2ca4b22e878011ef88fe0242ac120005",
            "create_date": "Fri, 11 Oct 2024 08:46:43 GMT",
            "create_time": 1728636403974,
            "id": "578d541e87ad11ef96b90242ac120006",
            "messages": [
                {
                    "content": "Hi! I am your assistant, can I help you?",
                    "role": "assistant"
                }
            ],
            "name": "new session",
            "reference": [],
            "update_date": "Fri, 11 Oct 2024 08:46:43 GMT",
            "update_time": 1728636403974,
            "user_id": ""
        }
    ]
}
```

失败

```json
{
    "code": 102,
    "message": "The session doesn't exist"
}
```

------

### 获取聊天助手的会话

**GET** `/api/v1/chats/{chat_id}/sessions/{session_id}`

获取指定聊天助手的特定会话，包括其消息、引用和头像。

#### 请求

- 方法: GET
- URL: `/api/v1/chats/{chat_id}/sessions/{session_id}`
- 请求头
  - `'Authorization: Bearer <YOUR_API_KEY>'`

##### 请求示例

```bash
curl --request GET \
     --url http://{address}/api/v1/chats/{chat_id}/sessions/{session_id} \
     --header 'Authorization: Bearer <YOUR_API_KEY>'
```

##### 请求参数

- `chat_id`: (*Path parameter*) 关联聊天助手的ID。
- `session_id`: (*Path parameter*) 要检索的会话的ID。

#### 响应

成功

```json
{
    "code": 0,
    "data": {
        "chat_id": "2ca4b22e878011ef88fe0242ac120005",
        "id": "4606b4ec87ad11efbc4f0242ac120006",
        "name": "new session",
        "avatar": "data:image/png;base64,...",
        "messages": [
            {
                "content": "Hi! I am your assistant, can I help you?",
                "role": "assistant"
            }
        ],
        "reference": []
    }
}
```

失败

```json
{
    "code": 102,
    "message": "Session not found!"
}
```

------

### 从聊天助手的会话中删除消息

**DELETE** `/api/v1/chats/{chat_id}/sessions/{session_id}/messages/{msg_id}`

从指定的聊天助手会话中删除用户消息及其配对的助手回复。

#### 请求

- 方法: DELETE
- URL: `/api/v1/chats/{chat_id}/sessions/{session_id}/messages/{msg_id}`
- 请求头
  - `'Authorization: Bearer <YOUR_API_KEY>'`

##### 请求示例

```bash
curl --request DELETE \
     --url http://{address}/api/v1/chats/{chat_id}/sessions/{session_id}/messages/{msg_id} \
     --header 'Authorization: Bearer <YOUR_API_KEY>'
```

##### 请求参数

- `chat_id`: (*Path parameter*) 关联聊天助手的ID。
- `session_id`: (*Path parameter*) 拥有该消息的会话的ID。
- `msg_id`: (*Path parameter*) 要删除的消息的ID。

#### 响应

成功：返回已更新的会话对象。

```json
{
    "code": 0,
    "data": {
        "chat_id": "2ca4b22e878011ef88fe0242ac120005",
        "id": "4606b4ec87ad11efbc4f0242ac120006",
        "messages": [],
        "reference": []
    }
}
```

失败

```json
{
    "code": 102,
    "message": "Session not found!"
}
```

------

### 更新聊天助手会话中的消息反馈

**PUT** `/api/v1/chats/{chat_id}/sessions/{session_id}/messages/{msg_id}/feedback`

更新指定聊天助手会话中助手消息的反馈。

#### 请求

- 方法: PUT
- URL: `/api/v1/chats/{chat_id}/sessions/{session_id}/messages/{msg_id}/feedback`
- 请求头
  - `'Content-Type: application/json'`
  - `'Authorization: Bearer <YOUR_API_KEY>'`
- 请求体
  - `"thumbup"`: `boolean`
  - `"feedback"`: `string` （可选）

##### 请求示例

```bash
curl --request PUT \
     --url http://{address}/api/v1/chats/{chat_id}/sessions/{session_id}/messages/{msg_id}/feedback \
     --header 'Content-Type: application/json' \
     --header 'Authorization: Bearer <YOUR_API_KEY>' \
     --data '{
          "thumbup": false,
          "feedback": "The answer missed the cited document."
     }'
```

##### 请求参数

- `chat_id`: (*Path parameter*) 关联聊天助手的ID。
- `session_id`: (*Path parameter*) 拥有该消息的会话的ID。
- `msg_id`: (*Path parameter*) 要更新的助手消息的ID。
- `"thumbup"`: (*Body parameter*), `boolean` 助手消息是否被标记为积极反馈。
- `"feedback"`: (*Body parameter*), `string` 可选的反馈文本，通常在 `"thumbup"` 为 `false` 时使用。

#### 响应

成功：返回已更新的会话对象。

```json
{
    "code": 0,
    "data": {
        "chat_id": "2ca4b22e878011ef88fe0242ac120005",
        "id": "4606b4ec87ad11efbc4f0242ac120006",
        "messages": [
            {
                "id": "message-id",
                "role": "assistant",
                "content": "Here is the answer.",
                "thumbup": false,
                "feedback": "The answer missed the cited document."
            }
        ]
    }
}
```

失败

```json
{
    "code": 102,
    "message": "Session not found!"
}
```

------

### 删除聊天助手的会话

**DELETE** `/api/v1/chats/{chat_id}/sessions`

按ID删除聊天助手的会话。

#### 请求

- 方法: DELETE
- URL: `/api/v1/chats/{chat_id}/sessions`
- 请求头
  - `'content-Type: application/json'`
  - `'Authorization: Bearer <YOUR_API_KEY>'`
- 请求体
  - `"ids"`: `list[string]`
  - `"delete_all"`: `boolean`

##### 请求示例

```bash
curl --request DELETE \
     --url http://{address}/api/v1/chats/{chat_id}/sessions \
     --header 'Content-Type: application/json' \
     --header 'Authorization: Bearer <YOUR_API_KEY>' \
     --data '
     {
          "ids": ["test_1", "test_2"]
     }'
curl --request DELETE \
     --url http://{address}/api/v1/chats/{chat_id}/sessions \
     --header 'Content-Type: application/json' \
     --header 'Authorization: Bearer <YOUR_API_KEY>' \
     --data '{
          "delete_all": true
     }'
```

##### 请求参数

- `chat_id`: (*Path parameter*) 关联聊天助手的ID。

- ```
  "ids"
  ```

  : (

  Body Parameter

  ),

   

  ```
  list[string]
  ```

   

  要删除的会话的ID。

  - 如果省略，或设置为 `null` 或空数组，则不删除任何会话。
  - 如果提供了ID数组，则只删除与这些ID匹配的会话。

- `"delete_all"`: (*Body Parameter*), `boolean` 当 `"ids"` 被省略，或设置为 `null` 或空数组时，是否删除指定聊天助手的所有会话。默认为 `false`。

#### 响应

成功

```json
{
    "code": 0
}
```

失败

```json
{
    "code": 102,
    "message": "The chat doesn't own the session"
}
```

------

### 与聊天助手对话

**POST** `/api/v1/chat/completions`

启动聊天完成请求。同一端点支持三种模式

已弃用

之前的端点 `POST /api/v1/chats/{chat_id}/completions` 已废弃。请使用此端点。

- 无 `chat_id`：直接与租户的默认聊天模型对话。
- 有 `chat_id` 但无 `session_id`：使用该聊天的配置并自动创建一个新会话。
- 同时有 `chat_id` 和 `session_id`：继续现有聊天会话。

注意

- 在流式模式下，并非所有响应都包含引用，因为这取决于系统的判断。

- 在流式模式下，最后一条消息是空消息

  ```json
  data:
  {
    "code": 0,
    "data": true
  }
  ```

#### 请求

- 方法: POST
- URL: `/api/v1/chat/completions`
- 请求头
  - `'content-Type: application/json'`
  - `'Authorization: Bearer <YOUR_API_KEY>'`
- 请求体
  - `"messages"`: `list[object]`
  - `"question"`: `string`
  - `"stream"`: `boolean`
  - `"chat_id"`: `string` （可选）
  - `"session_id"`: `string` （可选）
  - `"llm_id"`: `string` （可选）
  - `"pass_all_history_messages"`: `boolean` （可选）

##### 请求示例

```bash
curl --request POST \
     --url http://{address}/api/v1/chat/completions \
     --header 'Content-Type: application/json' \
     --header 'Authorization: Bearer <YOUR_API_KEY>' \
     --data-binary '
     {
          "messages": [
              {
                  "role": "user",
                  "content": "Who are you?"
              }
          ]
     }'
curl --request POST \
     --url http://{address}/api/v1/chat/completions \
     --header 'Content-Type: application/json' \
     --header 'Authorization: Bearer <YOUR_API_KEY>' \
     --data-binary '
     {
          "chat_id": "{chat_id}",
          "stream": true,
          "session_id":"9fa7691cb85c11ef9c5f0242ac120005",
          "messages": [
              {
                  "role": "user",
                  "content": "Who are you?"
              }
          ]
     }'
```

##### 请求参数

- `"messages"`: (*Body Parameter*), `list[object]` 当 `pass_all_history_messages` 为 `true` 时，为最新的用户消息，或发送给模型的对话消息。`messages` 或 `question` 之一是必需的。

- `"question"`: (*Body Parameter*), `string` 最新的用户问题。这相当于传递 `messages: [{"role": "user", "content": question}]`。

- ```
  "stream"
  ```

  : (

  Body Parameter

  ),

   

  ```
  boolean
  ```

   

  指示是否以流式方式输出响应

  - `true`: 启用流式传输（默认）。
  - `false`: 禁用流式传输。

- `"chat_id"`: (*Body Parameter*) 可选的聊天助手ID。如果省略，则直接使用租户的默认聊天模型。

- `"session_id"`: (*Body Parameter*) 可选的会话ID。如果提供了 `chat_id` 但省略了 `session_id`，将自动生成一个新会话。

- `"llm_id"`: (*Body Parameter*), `string` 当此请求应使用特定聊天模型时，可选的模型覆盖。

- `"pass_all_history_messages"`: (*Body Parameter*), `boolean` 当提供了 `chat_id` 和 `session_id` 时，默认为 `false`，因此服务器使用存储的会话历史记录和请求中的最新用户消息。设置为 `true` 可替换/使用提交的完整 `messages` 历史记录，并覆盖存储的会话历史记录。

#### 响应

无 `chat_id` 或 `session_id` 成功

```json
data:{
    "code": 0,
    "message": "",
    "data": {
        "answer": "I am an assistant powered by the tenant's default chat model.",
        "reference": {},
        "audio_binary": null,
        "id": "b01eed84b85611efa0e90242ac120005",
        "session_id": ""
    }
}
data:{
    "code": 0,
    "message": "",
    "data": true
}
```

有 `chat_id` 和 `session_id` 成功

```json
data:{
    "code": 0,
    "data": {
        "answer": "I am an intelligent assistant designed to help answer questions by summarizing content from a",
        "reference": {},
        "audio_binary": null,
        "id": "a84c5dd4-97b4-4624-8c3b-974012c8000d",
        "session_id": "82b0ab2a9c1911ef9d870242ac120006"
    }
}
data:{
    "code": 0,
    "data": {
        "answer": "I am an intelligent assistant designed to help answer questions by summarizing content from a knowledge base. My responses are based on the information available in the knowledge base and",
        "reference": {},
        "audio_binary": null,
        "id": "a84c5dd4-97b4-4624-8c3b-974012c8000d",
        "session_id": "82b0ab2a9c1911ef9d870242ac120006"
    }
}
data:{
    "code": 0,
    "data": {
        "answer": "I am an intelligent assistant designed to help answer questions by summarizing content from a knowledge base. My responses are based on the information available in the knowledge base and any relevant chat history.",
        "reference": {},
        "audio_binary": null,
        "id": "a84c5dd4-97b4-4624-8c3b-974012c8000d",
        "session_id": "82b0ab2a9c1911ef9d870242ac120006"
    }
}
data:{
    "code": 0,
    "data": {
        "answer": "I am an intelligent assistant designed to help answer questions by summarizing content from a knowledge base ##0$$. My responses are based on the information available in the knowledge base and any relevant chat history.",
        "reference": {
            "total": 1,
            "chunks": [
                {
                    "id": "faf26c791128f2d5e821f822671063bd",
                    "content": "xxxxxxxx",
                    "document_id": "dd58f58e888511ef89c90242ac120006",
                    "document_name": "1.txt",
                    "dataset_id": "8e83e57a884611ef9d760242ac120006",
                    "image_id": "",
                    "url": null,
                    "similarity": 0.7,
                    "vector_similarity": 0.0,
                    "term_similarity": 1.0,
                    "doc_type": [],
                    "positions": [
                        ""
                    ]
                }
            ],
            "doc_aggs": [
                {
                    "doc_name": "1.txt",
                    "doc_id": "dd58f58e888511ef89c90242ac120006",
                    "count": 1
                }
            ]
        },
        "prompt": "xxxxxxxxxxx",
        "created_at": 1755055623.6401553,
        "id": "a84c5dd4-97b4-4624-8c3b-974012c8000d",
        "session_id": "82b0ab2a9c1911ef9d870242ac120006"
    }
}
data:{
    "code": 0,
    "data": true
}
```

失败

```json
{
    "code": 102,
    "message": "Please input your question."
}
```