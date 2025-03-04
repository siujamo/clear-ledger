import { useEffect, useState } from "react"
import { Outlet } from "react-router"
import { useNavigate } from "react-router-dom"
import { Button, Layout, Menu, message } from "antd"
import { Content, Header } from "antd/es/layout/layout"
import Sidebar from "antd/es/layout/Sider"
import { AxiosError } from "axios"
import { useAppDispatch, useAppSelector } from "@/hooks/store"
import { logout } from "@/store/auth-slice"
import * as LedgerApi from "@/api/ledger"
import "./index.css"
import staticMenuItems from "./menu-item.ts"
import { setLedgers } from "@/store/ledger-slice.ts"
import { MenuItem } from "@/types"
import { JoinLedgerDialogue } from "@/components/join-ledger-dialogue"
import { CreateLedgerDialogue } from "@/components/create-ledger-dialogue"

export const DashboardLayout = () => {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()

  const [isJoinLedgerDialogueOpen, setIsJoinLedgerDialogueOpen] =
    useState<boolean>(false)
  const [isCreateLedgerDialogueOpen, setIsCreateLedgerDialogueOpen] =
    useState<boolean>(false)

  const _logout = async () => {
    dispatch(logout())
    message.success("登出成功")
    navigate("/login", { replace: true })
  }

  const ledgers = useAppSelector((state) => state.ledger.ledgers)

  // 在组件挂载时获取账本数据
  useEffect(() => {
    ;(async () => {
      try {
        const ledgerData = await LedgerApi.getLedgers()
        dispatch(setLedgers(ledgerData))
      } catch (error) {
        // console.error("获取账本失败:", error)
        if (error instanceof AxiosError) {
          message.error(error.response?.data.message)
        } else {
          message.error("获取账本失败")
        }
      }
    })()
  }, [dispatch])

  // 当加入或创建的账本多于3个时禁用加入和创建账本功能
  const isLimitReached = ledgers.length >= 3
  // 动态生成菜单项：静态项 + 账本项
  const menuItems = [
    ...ledgers.map(
      (ledger) =>
        ({
          key: `ledger#${ledger.id}`,
          label: ledger.name,
        }) as MenuItem
    ),
    ...staticMenuItems.map(
      (item) =>
        ({
          ...item,
          disabled:
            isLimitReached &&
            ["join-ledger", "create-ledger"].includes(item!.key as string),
        }) as MenuItem
    ),
  ]

    const onMenuItemClicked = ({ key }: { key: string }) => {
        if (key === "join-ledger") {
            setIsJoinLedgerDialogueOpen(true)
        } else if (key === "create-ledger") {
            setIsCreateLedgerDialogueOpen(true)
        } else if (key.startsWith("ledger#")) {
            const ledgerId = key.split("#")[1]
            navigate(`/ledgers/${ledgerId}`)
        }
    }

  return (
    <Layout className="dashboard-wrapper">
      <Header className="dashboard-header">
        Clear Ledger
        <Button type="text" onClick={_logout}>
          注销
        </Button>
      </Header>
      <Layout className="dashboard-content-wrapper">
        <Sidebar className="dashboard-sidebar-wrapper">
          <Menu
            items={menuItems}
            className="sidebar-menu"
            onClick={onMenuItemClicked}
          />
        </Sidebar>
        <Content className="dashboard-content">
          <Outlet />
        </Content>
      </Layout>

      <JoinLedgerDialogue
        open={isJoinLedgerDialogueOpen}
        onClose={() => setIsJoinLedgerDialogueOpen(false)}
      />
      <CreateLedgerDialogue
        open={isCreateLedgerDialogueOpen}
        onClose={() => setIsCreateLedgerDialogueOpen(false)}
      />
    </Layout>
  )
}
