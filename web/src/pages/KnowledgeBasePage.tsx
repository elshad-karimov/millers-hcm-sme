import { useEffect, useState } from 'react'
import {
  Card,
  Table,
  Tag,
  Button,
  Space,
  Input,
  Select,
  Modal,
  Form,
  Drawer,
  Typography,
  App as AntdApp,
  Tabs,
  Row,
  Col,
  Descriptions,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  SearchOutlined,
  PlusOutlined,
  EyeOutlined,
  EditOutlined,
  CheckOutlined,
  InboxOutlined,
  LikeOutlined,
  DislikeOutlined,
} from '@ant-design/icons'
import { knowledgeApi, type KnowledgeArticle, CATEGORIES } from '../api/knowledge'
import { useAuth } from '../auth/AuthContext'

const { TextArea } = Input
const { Paragraph, Title } = Typography

export default function KnowledgeBasePage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const isHr = hasRole('HR_ADMIN', 'HR_SPECIALIST')

  const [loading, setLoading] = useState(false)
  const [articles, setArticles] = useState<KnowledgeArticle[]>([])
  const [searchKeyword, setSearchKeyword] = useState('')
  const [selectedCategory, setSelectedCategory] = useState<string | undefined>()
  const [selectedArticle, setSelectedArticle] = useState<KnowledgeArticle | null>(null)
  const [viewDrawerOpen, setViewDrawerOpen] = useState(false)
  const [editorOpen, setEditorOpen] = useState(false)
  const [editorMode, setEditorMode] = useState<'create' | 'edit'>('create')
  const [currentTab, setCurrentTab] = useState('published')
  const [form] = Form.useForm()

  useEffect(() => {
    loadArticles()
  }, [currentTab])

  const loadArticles = async () => {
    setLoading(true)
    try {
      const endpoint = isHr && currentTab === 'all' ? knowledgeApi.listAll : knowledgeApi.listPublished
      const { data } = await endpoint()
      setArticles(data)
    } catch (err: any) {
      message.error('Failed to load articles: ' + (err.message || ''))
    } finally {
      setLoading(false)
    }
  }

  const handleSearch = async () => {
    if (!searchKeyword.trim()) {
      loadArticles()
      return
    }
    setLoading(true)
    try {
      const { data } = await knowledgeApi.search(searchKeyword)
      setArticles(data)
    } catch (err: any) {
      message.error('Search failed: ' + (err.message || ''))
    } finally {
      setLoading(false)
    }
  }

  const openArticle = async (article: KnowledgeArticle) => {
    try {
      const { data } = await knowledgeApi.get(article.id)
      setSelectedArticle(data)
      setViewDrawerOpen(true)
    } catch (err: any) {
      message.error('Failed to open article: ' + (err.message || ''))
    }
  }

  const handleVote = async (helpful: boolean) => {
    if (!selectedArticle) return
    try {
      await knowledgeApi.vote(selectedArticle.id, helpful)
      message.success('Thank you for your feedback')
      loadArticles()
    } catch (err: any) {
      message.error('Vote failed: ' + (err.message || ''))
    }
  }

  const openEditor = (mode: 'create' | 'edit', article?: KnowledgeArticle) => {
    setEditorMode(mode)
    if (mode === 'edit' && article) {
      form.setFieldsValue({
        code: article.code,
        title: article.title,
        summary: article.summary || '',
        category: article.category,
        tags: article.tags || '',
        body: article.body,
      })
      setSelectedArticle(article)
    } else {
      form.resetFields()
      form.setFieldsValue({ category: 'General' })
      setSelectedArticle(null)
    }
    setEditorOpen(true)
  }

  const saveArticle = async () => {
    try {
      const values = await form.validateFields()
      if (editorMode === 'create') {
        await knowledgeApi.create(values)
        message.success('Article created')
      } else if (selectedArticle) {
        await knowledgeApi.update(selectedArticle.id, values)
        message.success('Article updated')
      }
      setEditorOpen(false)
      form.resetFields()
      loadArticles()
    } catch (err: any) {
      message.error('Save failed: ' + (err.message || ''))
    }
  }

  const publishArticle = async (id: string) => {
    try {
      await knowledgeApi.publish(id)
      message.success('Article published')
      loadArticles()
      setViewDrawerOpen(false)
    } catch (err: any) {
      message.error('Publish failed: ' + (err.message || ''))
    }
  }

  const archiveArticle = async (id: string) => {
    try {
      await knowledgeApi.archive(id)
      message.success('Article archived')
      loadArticles()
      setViewDrawerOpen(false)
    } catch (err: any) {
      message.error('Archive failed: ' + (err.message || ''))
    }
  }

  const statusColor = (status: string) => {
    switch (status) {
      case 'PUBLISHED': return 'success'
      case 'DRAFT': return 'default'
      case 'ARCHIVED': return 'error'
      default: return 'default'
    }
  }

  const filteredArticles = selectedCategory
    ? articles.filter(a => a.category === selectedCategory)
    : articles

  const columns: ColumnsType<KnowledgeArticle> = [
    {
      title: 'Title',
      dataIndex: 'title',
      key: 'title',
      render: (text, record) => (
        <a onClick={() => openArticle(record)}>{text}</a>
      ),
    },
    {
      title: 'Category',
      dataIndex: 'category',
      key: 'category',
      render: (cat) => <Tag>{cat}</Tag>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) => <Tag color={statusColor(status)}>{status}</Tag>,
    },
    {
      title: 'Views',
      dataIndex: 'viewCount',
      key: 'viewCount',
      align: 'center',
    },
    {
      title: 'Helpful',
      dataIndex: 'helpfulVotes',
      key: 'helpfulVotes',
      align: 'center',
      render: (votes) => (
        <Space>
          <LikeOutlined /> {votes}
        </Space>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openArticle(record)}>
            View
          </Button>
          {isHr && (
            <>
              <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEditor('edit', record)}>
                Edit
              </Button>
              {record.status === 'DRAFT' && (
                <Button type="link" size="small" icon={<CheckOutlined />} onClick={() => publishArticle(record.id)}>
                  Publish
                </Button>
              )}
              {record.status === 'PUBLISHED' && (
                <Button type="link" size="small" icon={<InboxOutlined />} onClick={() => archiveArticle(record.id)}>
                  Archive
                </Button>
              )}
            </>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Card
        title="Knowledge Base"
        extra={
          isHr && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor('create')}>
              Create Article
            </Button>
          )
        }
      >
        {isHr && (
          <Tabs
            activeKey={currentTab}
            onChange={setCurrentTab}
            style={{ marginBottom: 16 }}
            items={[
              { key: 'published', label: 'Published' },
              { key: 'all', label: 'All Articles' },
            ]}
          />
        )}

        <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
          <Col flex="auto">
            <Input
              placeholder="Search articles..."
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              onPressEnter={handleSearch}
              prefix={<SearchOutlined />}
            />
          </Col>
          <Col>
            <Button onClick={handleSearch}>Search</Button>
          </Col>
        </Row>

        <Row style={{ marginBottom: 16 }}>
          <Space wrap>
            <Tag.CheckableTag
              checked={!selectedCategory}
              onChange={() => setSelectedCategory(undefined)}
            >
              All
            </Tag.CheckableTag>
            {CATEGORIES.map(cat => (
              <Tag.CheckableTag
                key={cat}
                checked={selectedCategory === cat}
                onChange={() => setSelectedCategory(selectedCategory === cat ? undefined : cat)}
              >
                {cat}
              </Tag.CheckableTag>
            ))}
          </Space>
        </Row>

        <Table
          dataSource={filteredArticles}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
        />
      </Card>

      {/* View Drawer */}
      <Drawer
        title="Article"
        placement="right"
        width="50%"
        open={viewDrawerOpen}
        onClose={() => setViewDrawerOpen(false)}
        extra={
          isHr && selectedArticle && (
            <Space>
              <Button size="small" icon={<EditOutlined />} onClick={() => openEditor('edit', selectedArticle)}>
                Edit
              </Button>
              {selectedArticle.status === 'DRAFT' && (
                <Button type="primary" size="small" icon={<CheckOutlined />} onClick={() => publishArticle(selectedArticle.id)}>
                  Publish
                </Button>
              )}
              {selectedArticle.status === 'PUBLISHED' && (
                <Button size="small" icon={<InboxOutlined />} onClick={() => archiveArticle(selectedArticle.id)}>
                  Archive
                </Button>
              )}
            </Space>
          )
        }
      >
        {selectedArticle && (
          <div>
            <Title level={3}>{selectedArticle.title}</Title>
            <Space style={{ marginBottom: 16 }}>
              <Tag>{selectedArticle.category}</Tag>
              <Tag color={statusColor(selectedArticle.status)}>{selectedArticle.status}</Tag>
            </Space>
            {selectedArticle.summary && (
              <Paragraph type="secondary" style={{ marginBottom: 16 }}>
                {selectedArticle.summary}
              </Paragraph>
            )}
            <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 24 }}>
              {selectedArticle.body}
            </Paragraph>
            <Descriptions size="small" bordered column={3} style={{ marginBottom: 16 }}>
              <Descriptions.Item label="Views">{selectedArticle.viewCount}</Descriptions.Item>
              <Descriptions.Item label="Helpful">{selectedArticle.helpfulVotes}</Descriptions.Item>
              <Descriptions.Item label="Not Helpful">{selectedArticle.notHelpfulVotes}</Descriptions.Item>
            </Descriptions>
            <Space>
              <Button icon={<LikeOutlined />} onClick={() => handleVote(true)}>
                Helpful
              </Button>
              <Button icon={<DislikeOutlined />} onClick={() => handleVote(false)}>
                Not Helpful
              </Button>
            </Space>
          </div>
        )}
      </Drawer>

      {/* Editor Modal */}
      <Modal
        title={editorMode === 'create' ? 'Create Article' : 'Edit Article'}
        open={editorOpen}
        onOk={saveArticle}
        onCancel={() => {
          setEditorOpen(false)
          form.resetFields()
        }}
        width={800}
      >
        <Form form={form} layout="vertical">
          <Form.Item label="Code" name="code" rules={[{ required: true, message: 'Code is required' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Title" name="title" rules={[{ required: true, message: 'Title is required' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Summary" name="summary">
            <TextArea rows={2} />
          </Form.Item>
          <Form.Item label="Category" name="category" rules={[{ required: true, message: 'Category is required' }]}>
            <Select>
              {CATEGORIES.map(cat => (
                <Select.Option key={cat} value={cat}>
                  {cat}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item label="Tags (comma-separated)" name="tags">
            <Input />
          </Form.Item>
          <Form.Item label="Body" name="body" rules={[{ required: true, message: 'Body is required' }]}>
            <TextArea rows={10} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
