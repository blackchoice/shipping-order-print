/**
 * 商城系统集成 - 本地打印客户端库
 *
 * 使用方法：
 * 1. 在商城订单页面引入此文件：<script src="/js/shipping-print-client.js"></script>
 * 2. 调用 ShippingPrintClient.printOrder(orderData) 即可打印
 */

const ShippingPrintClient = (function() {
  // 配置
  const CONFIG = {
    // 本地打印服务地址
    printServiceUrl: 'http://localhost:8080',

    // 公司抬头
    companyTitle: '怀化市兴隆农业开发有限公司出货单',

    // 首选打印机名称关键词
    preferredPrinterKeywords: ['DPK700', 'NFCP'],

    // 调试模式
    debug: true
  };

  /**
   * 日志输出
   */
  function log(message, data) {
    if (CONFIG.debug) {
      console.log(`[ShippingPrint] ${message}`, data || '');
    }
  }

  /**
   * 检查本地打印服务是否可用
   * @returns {Promise<boolean>}
   */
  async function checkService() {
    try {
      const response = await fetch(`${CONFIG.printServiceUrl}/api/printers`, {
        method: 'GET',
        mode: 'cors'
      });
      return response.ok;
    } catch (error) {
      log('打印服务检查失败', error);
      return false;
    }
  }

  /**
   * 获取可用打印机列表
   * @returns {Promise<string[]>}
   */
  async function listPrinters() {
    const response = await fetch(`${CONFIG.printServiceUrl}/api/printers`);
    if (!response.ok) {
      throw new Error('无法获取打印机列表');
    }
    const data = await response.json();
    return data.printers || [];
  }

  /**
   * 选择打印机（优先选择 DPK700）
   * @param {string[]} printers
   * @returns {string|null}
   */
  function selectPrinter(printers) {
    if (!printers || printers.length === 0) {
      return null;
    }

    // 尝试匹配首选打印机
    for (const keyword of CONFIG.preferredPrinterKeywords) {
      const found = printers.find(p => p.includes(keyword));
      if (found) {
        return found;
      }
    }

    // 返回第一个可用打印机
    return printers[0];
  }

  /**
   * 转换商城订单数据为打印格式
   * @param {Object} orderData - 商城订单数据
   * @returns {Object} - 打印服务需要的格式
   *
   * orderData 示例：
   * {
   *   orderNo: 'XS-202607010206',
   *   customerName: '本部食堂',
   *   date: '2026-07-01',
   *   deliverer: '张三',
   *   receiver: '李四',
   *   items: [
   *     { productName: '牛腩', unit: '公斤', quantity: 4, unitPrice: 70, remark: '' }
   *   ]
   * }
   */
  function formatOrderData(orderData) {
    return {
      companyTitle: CONFIG.companyTitle,
      customerName: orderData.customerName || '',
      orderNo: orderData.orderNo || '',
      date: orderData.date || new Date().toISOString().split('T')[0],
      deliverer: orderData.deliverer || '',
      receiver: orderData.receiver || '',
      lines: (orderData.items || []).map(item => ({
        productName: item.productName || item.name || '',
        unit: item.unit || '件',
        quantity: parseFloat(item.quantity) || 0,
        unitPrice: parseFloat(item.unitPrice) || parseFloat(item.price) || 0,
        remark: item.remark || ''
      }))
    };
  }

  /**
   * 执行打印
   * @param {Object} orderData - 商城订单数据
   * @param {string} [printerName] - 可选：指定打印机名称
   * @returns {Promise<Object>} - { success: boolean, message: string, printerName?: string }
   */
  async function printOrder(orderData, printerName) {
    try {
      log('开始打印', orderData);

      // 1. 检查服务
      const serviceAvailable = await checkService();
      if (!serviceAvailable) {
        return {
          success: false,
          message: '本地打印服务未启动。请确保打印服务正在运行。\n\n启动方式：\n在本地 Windows 机器上运行打印服务程序。'
        };
      }

      // 2. 获取打印机
      if (!printerName) {
        const printers = await listPrinters();
        printerName = selectPrinter(printers);

        if (!printerName) {
          return {
            success: false,
            message: '未找到可用的打印机。请检查打印机是否正确安装。'
          };
        }
      }

      log('使用打印机', printerName);

      // 3. 格式化数据
      const printData = formatOrderData(orderData);
      log('打印数据', printData);

      // 4. 发送打印请求
      const url = `${CONFIG.printServiceUrl}/api/print/shipping-order?printerName=${encodeURIComponent(printerName)}`;
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(printData)
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.message || `打印失败 (HTTP ${response.status})`);
      }

      const result = await response.json();
      log('打印成功', result);

      return {
        success: true,
        message: result.message || '打印成功',
        printerName: printerName
      };

    } catch (error) {
      log('打印失败', error);
      return {
        success: false,
        message: error.message || '打印失败，请稍后重试'
      };
    }
  }

  /**
   * 显示打印状态提示
   * @param {Object} result - printOrder 的返回结果
   * @param {HTMLElement} [container] - 可选：显示消息的容器元素
   */
  function showStatus(result, container) {
    const message = result.success
      ? `✓ ${result.message} → ${result.printerName}`
      : `✗ ${result.message}`;

    if (container) {
      container.textContent = message;
      container.style.color = result.success ? 'green' : 'red';
      container.style.display = 'block';
    } else {
      // 使用浏览器原生提示
      if (result.success) {
        alert(message);
      } else {
        alert(message);
      }
    }
  }

  /**
   * 预览打印布局（不实际打印）
   * @param {Object} orderData - 商城订单数据
   * @returns {Promise<Object>} - { lines: string[], pageWidthChars: number, totalLines: number }
   */
  async function previewLayout(orderData) {
    const printData = formatOrderData(orderData);

    const response = await fetch(`${CONFIG.printServiceUrl}/api/print/shipping-order/layout`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(printData)
    });

    if (!response.ok) {
      throw new Error('获取预览失败');
    }

    return await response.json();
  }

  // 公开 API
  return {
    // 配置
    config: CONFIG,

    // 主要功能
    printOrder: printOrder,
    previewLayout: previewLayout,

    // 辅助功能
    checkService: checkService,
    listPrinters: listPrinters,
    showStatus: showStatus
  };
})();

// 如果是模块环境，导出
if (typeof module !== 'undefined' && module.exports) {
  module.exports = ShippingPrintClient;
}
