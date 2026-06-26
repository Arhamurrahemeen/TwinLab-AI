import { useEffect, useRef, useState } from 'react'

const MAX_MESSAGES = 200

export function useDeviceSocket(deviceId) {
  const [messages, setMessages] = useState([])
  const wsRef = useRef(null)

  useEffect(() => {
    if (!deviceId) {
      setMessages([])
      return
    }

    setMessages([])
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const ws = new WebSocket(`${protocol}//${window.location.host}/ws/${deviceId}`)
    wsRef.current = ws

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        setMessages(prev => {
          const next = [...prev, data]
          return next.length > MAX_MESSAGES ? next.slice(-MAX_MESSAGES) : next
        })
      } catch {}
    }

    return () => {
      ws.close()
      wsRef.current = null
    }
  }, [deviceId])

  return messages
}
