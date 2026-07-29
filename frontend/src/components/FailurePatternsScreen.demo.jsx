import React from 'react';
import {
  Alert,
  Card,
  PageHeader,
  Split,
} from '../design-system';
import { BarChart } from '../design-system';

/**
 * Demo page to showcase the Failure Patterns screen with dynamic color depth
 * and font size based on values.
 * 
 * 需求实现：
 * 1. 数字越大，柱子的颜色要越深
 * 2. 数字越大，对应的原因的字体也要越大
 */
export default function FailurePatternsScreenDemo() {
  // Mock data with different counts
  const mockData = [
    { code: 'VER_LIMIT_EXCEEDED', count: 150, kind: 'failure' },
    { code: 'VER_AGE_MISMATCH', count: 85, kind: 'failure' },
    { code: 'VER_DOCUMENT_EXPIRED', count: 120, kind: 'failure' },
    { code: 'VER_INCOME_LOW', count: 45, kind: 'failure' },
    { code: 'VER_CREDIT_CHECK', count: 200, kind: 'failure' },
    { code: 'VER_ADDRESS_INVALID', count: 30, kind: 'failure' },
    { code: 'VER_NAME_MISMATCH', count: 95, kind: 'failure' },
    { code: 'VER_PHONE_INVALID', count: 65, kind: 'failure' },
  ];

  // Prepare data for bar chart with styling
  const chartData = mockData
    .filter((item) => item.kind !== 'review')
    .map((item) => ({
      label: item.code.replace('VER_', ''),
      value: item.count,
    }));

  // Calculate min and max values for color and font scaling
  const values = chartData.map((d) => d.value);
  const minValue = Math.min(...values, Infinity);
  const maxValue = Math.max(...values, -Infinity);
  const valueRange = maxValue - minValue || 1;

  // Add color depth and font size to each chart item
  const chartDataWithStyling = chartData.map((item) => {
    const normalized = (item.value - minValue) / valueRange;
    // Map normalized value (0-1) to color depth (0-4, where higher = darker)
    const colorDepth = Math.round(normalized * 4);
    // Map normalized value to font size: 0.875rem to 1.125rem
    const fontSize = 0.875 + normalized * 0.25;
    return {
      ...item,
      colorDepth,
      fontSize,
    };
  });

  return (
    <>
      <PageHeader
        title="Failure Patterns (Demo)"
        lede="Color depth and font size scale with value"
      />

      <Alert tone="info" title="Demo Mode">
        This is a demo using mock data. The actual screen loads data from the backend.
        Notice how:
        <ul>
          <li>Larger counts have darker colored bars</li>
          <li>Reason labels with larger counts have larger fonts</li>
        </ul>
      </Alert>

      <Split
        sidebar={
          <Card title="Mock Data Values">
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              {mockData.map((item) => (
                <div key={`${item.code}`} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: '0.9375rem', color: '#333' }}>
                    {item.code}
                  </span>
                  <span style={{ fontSize: '0.9375rem', fontWeight: 500, color: '#17a697' }}>
                    {item.count}
                  </span>
                </div>
              ))}
            </div>
          </Card>
        }
      >
        <Card title="Visual Representation (Dynamic Color & Font)">
          <BarChart data={chartDataWithStyling} />
        </Card>
      </Split>

      <div style={{ marginTop: '2rem', padding: '1rem', backgroundColor: '#f5f5f5', borderRadius: '8px' }}>
        <h3>实现说明</h3>
        <p>
          在 FailurePatternsScreen.jsx 中：
        </p>
        <pre style={{ overflow: 'auto', backgroundColor: '#fff', padding: '1rem', borderRadius: '4px' }}>
{`// 计算规范化值（0-1）
const normalized = (item.value - minValue) / valueRange;

// 映射到颜色深度 (0-4)
const colorDepth = Math.round(normalized * 4);

// 映射到字体大小 (0.875rem - 1.125rem)
const fontSize = 0.875 + normalized * 0.25;`}
        </pre>
        <p>
          颜色深度对应的样式在 glass.css 中定义：
        </p>
        <ul>
          <li>Depth 0: --ds-series-1-lighter (最浅)</li>
          <li>Depth 1: --ds-series-1-light</li>
          <li>Depth 2: --ds-series-1 (中间)</li>
          <li>Depth 3: --ds-series-1-dark</li>
          <li>Depth 4: --ds-series-1-darker (最深)</li>
        </ul>
      </div>
    </>
  );
}
